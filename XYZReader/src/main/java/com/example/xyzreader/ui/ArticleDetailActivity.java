package com.example.xyzreader.ui;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.app.SharedElementCallback;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.view.OnApplyWindowInsetsListener;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.view.WindowInsetsCompat;
import android.support.v7.app.AppCompatActivity;
import android.transition.Transition;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.example.xyzreader.data.ItemsContract;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An activity representing a single Article detail screen, letting you swipe between articles.
 */
public class ArticleDetailActivity extends AppCompatActivity
        implements LoaderManager.LoaderCallbacks<Cursor> {

    public static final String EXTRA_STARTING_ITEM_POSITION = "extra_starting_item_position";
    public static final String EXTRA_CURRENT_ITEM_POSITION = "extra_current_item_position";

    private Cursor mCursor;
    private long mStartId;

    private long mSelectedItemId;

    private ViewPager mPager;
    private MyPagerAdapter mPagerAdapter;

    private int mStartingItemPosition;
    private int mCurrentItemPosition;

    private boolean mIsEnteringTransition;

    private ArticleDetailFragment mArticleDetailFragment;
    private boolean mIsReturning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_article_detail);

        mIsEnteringTransition = true;

        setTitle(null);

        getLoaderManager().initLoader(0, null, this);

        mPagerAdapter = new MyPagerAdapter(getFragmentManager());
        mPager = (ViewPager) findViewById(R.id.pager);
        if (mPager != null) {
            mPager.setAdapter(mPagerAdapter);
        }
        mPager.setPageMargin((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics()));
        mPager.setPageMarginDrawable(new ColorDrawable(0x22000000));

        mPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                if (mCursor != null) {
                    mCursor.moveToPosition(position);
                }

                mSelectedItemId = mCursor.getLong(ArticleLoader.Query._ID);
                mCurrentItemPosition = position;
            }
        });

        // Solution proposed by Android Team. Issue: https://code.google.com/p/android/issues/detail?id=180492
        ViewCompat.setOnApplyWindowInsetsListener(mPager,
                new OnApplyWindowInsetsListener() {
                    @Override
                    public WindowInsetsCompat onApplyWindowInsets(View v,
                                                                  WindowInsetsCompat insets) {
                        insets = ViewCompat.onApplyWindowInsets(v, insets);
                        if (insets.isConsumed()) {
                            return insets;
                        }

                        boolean consumed = false;
                        for (int i = 0, count = mPager.getChildCount(); i <  count; i++) {
                            ViewCompat.dispatchApplyWindowInsets(mPager.getChildAt(i), insets);
                            if (insets.isConsumed()) {
                                consumed = true;
                            }
                        }
                        return consumed ? insets.consumeSystemWindowInsets() : insets;
                    }
                });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().getSharedElementEnterTransition().addListener(new Transition.TransitionListener() {
                @Override
                public void onTransitionStart(Transition transition) {
                }

                @Override
                public void onTransitionEnd(Transition transition) {
                    if (mIsEnteringTransition) {
                        ((ArticleDetailFragment)getCurrentViewPagerFragment()).showElementsAfterTransition();
                        mIsEnteringTransition = false;
                    }
                }

                @Override
                public void onTransitionCancel(Transition transition) {
                }

                @Override
                public void onTransitionPause(Transition transition) {
                }

                @Override
                public void onTransitionResume(Transition transition) {
                }
            });
        }

        mStartingItemPosition = getIntent().getIntExtra(EXTRA_STARTING_ITEM_POSITION, 0);

        if (savedInstanceState == null) {
            mCurrentItemPosition = mStartingItemPosition;
            if (getIntent() != null && getIntent().getData() != null) {
                mStartId = ItemsContract.Items.getItemId(getIntent().getData());
                mSelectedItemId = mStartId;
            }
        } else {
            mCurrentItemPosition = savedInstanceState.getInt(EXTRA_CURRENT_ITEM_POSITION, 0);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            postponeEnterTransition();
            setEnterSharedElementCallback(mCallback);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newAllArticlesInstance(this);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        mCursor = cursor;
        mPagerAdapter.notifyDataSetChanged();

        // Select the start ID
        if (mStartId > 0) {
            mCursor.moveToFirst();
            // TODO: optimize
            while (!mCursor.isAfterLast()) {
                if (mCursor.getLong(ArticleLoader.Query._ID) == mStartId) {
                    final int position = mCursor.getPosition();
                    mPager.setCurrentItem(position, false);
                    break;
                }
                mCursor.moveToNext();
            }
            mStartId = 0;
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        mCursor = null;
        mPagerAdapter.notifyDataSetChanged();
    }

    @Override
    public void onBackPressed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && getCurrentViewPagerFragment() instanceof ArticleDetailFragment) {
            ((ArticleDetailFragment)getCurrentViewPagerFragment()).hideElementsBeforeTransition();
        }
        super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(EXTRA_CURRENT_ITEM_POSITION, mCurrentItemPosition);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void finishAfterTransition() {
        mIsReturning = true;
        Intent data = new Intent();
        data.putExtra(EXTRA_STARTING_ITEM_POSITION, mStartingItemPosition);
        data.putExtra(EXTRA_CURRENT_ITEM_POSITION, mCurrentItemPosition);
        setResult(RESULT_OK, data);

        super.finishAfterTransition();
    }

    public Fragment getCurrentViewPagerFragment() {
        return mPagerAdapter.getFragment(mPager.getCurrentItem());
    }

    //Callback to remap shared element transition
    @SuppressLint("NewApi")
    private final SharedElementCallback mCallback = new SharedElementCallback() {

        @Override
        public void onSharedElementStart(List<String> sharedElementNames, List<View> sharedElements, List<View> sharedElementSnapshots) {
            ((ArticleDetailFragment)getCurrentViewPagerFragment()).hideElementsBeforeTransition();
            super.onSharedElementStart(sharedElementNames, sharedElements, sharedElementSnapshots);
        }

        @Override
        public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
            if (mIsReturning) {
                ImageView sharedElement = mArticleDetailFragment.getImageView();
                if (sharedElement == null) {
                    names.clear();
                    sharedElements.clear();
                } else if (mStartingItemPosition != mCurrentItemPosition) {
                    names.clear();
                    names.add(String.valueOf(mSelectedItemId) + "_" + mCurrentItemPosition);
                    sharedElements.clear();
                    sharedElements.put(names.get(0), sharedElement);
                }
            }
        }
    };



    private class MyPagerAdapter extends FragmentStatePagerAdapter {

        HashMap<Integer, Fragment> mPageReferenceMap;

        public MyPagerAdapter(FragmentManager fm) {
            super(fm);
            this.mPageReferenceMap = new HashMap<Integer, Fragment>();
        }

        @Override
        public void setPrimaryItem(ViewGroup container, int position, Object object) {
            super.setPrimaryItem(container, position, object);
            mArticleDetailFragment = (ArticleDetailFragment) object;
        }

        @Override
        public Fragment getItem(int position) {
            mCursor.moveToPosition(position);
            return ArticleDetailFragment.newInstance(mCursor.getLong(ArticleLoader.Query._ID), mStartingItemPosition);
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            Fragment fragment = (Fragment) super.instantiateItem(container, position);
            mPageReferenceMap.put(position, fragment);
            return fragment;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            super.destroyItem(container, position, object);
            mPageReferenceMap.remove(position);
        }

        @Override
        public int getCount() {
            return (mCursor != null) ? mCursor.getCount() : 0;
        }

        public Fragment getFragment(int position) {
            return mPageReferenceMap.get(position);
        }
    }
}
