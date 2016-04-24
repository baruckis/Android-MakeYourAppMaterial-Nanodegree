package com.example.xyzreader.ui;

import android.annotation.SuppressLint;
import android.app.ActivityOptions;
import android.app.LoaderManager;
import android.app.SharedElementCallback;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.text.format.DateUtils;
import android.transition.Transition;
import android.transition.TransitionInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.example.xyzreader.data.ItemsContract;
import com.example.xyzreader.data.UpdaterService;

import java.util.List;
import java.util.Map;

/**
 * An activity representing a list of Articles. This activity has different presentations for
 * handset and tablet-size devices. On handsets, the activity presents a list of items, which when
 * touched, lead to a {@link ArticleDetailActivity} representing item details. On tablets, the
 * activity presents a grid of items as cards.
 */
public class ArticleListActivity extends AppCompatActivity implements
        LoaderManager.LoaderCallbacks<Cursor> {

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mRecyclerView;

    private boolean mIsRefreshing = false;

    private int mCurrentPosition;
    private Bundle mReenterPositions;
    private Cursor mCursor;
    private boolean mIsPostponed;

    private BroadcastReceiver mRefreshingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (UpdaterService.BROADCAST_ACTION_STATE_CHANGE.equals(intent.getAction())) {
                mIsRefreshing = intent.getBooleanExtra(UpdaterService.EXTRA_REFRESHING, false);
                updateRefreshingUI();
            }
        }
    };

    @SuppressLint("NewApi")
    private final SharedElementCallback mCallbackExit = new SharedElementCallback() {
        @Override
        public void onMapSharedElements(final List<String> names, final Map<String, View> sharedElements) {
            if (mReenterPositions != null) {
                int startPosition = mReenterPositions.getInt(ArticleDetailActivity.EXTRA_STARTING_ITEM_POSITION);
                mCurrentPosition = mReenterPositions.getInt(ArticleDetailActivity.EXTRA_CURRENT_ITEM_POSITION);
                if (startPosition != mCurrentPosition) {

                    mCursor.moveToPosition(mCurrentPosition);
                    String transitionNewName = String.valueOf(mCursor.getLong(ArticleLoader.Query._ID))+ "_" + mCurrentPosition;

                    ViewHolder viewHolder = (ViewHolder) mRecyclerView.findViewHolderForAdapterPosition(mCurrentPosition);
                    View newSharedElement = null;
                    if (viewHolder!=null) {
                        newSharedElement = viewHolder.itemView.findViewById(R.id.thumbnail);
                    }

                    if (newSharedElement != null) {
                        names.clear();
                        names.add(transitionNewName);
                        sharedElements.clear();
                        sharedElements.put(transitionNewName, newSharedElement);
                    }
                    mReenterPositions = null;
                } else {

                    View navBar = findViewById(android.R.id.navigationBarBackground);
                    View statusBar = findViewById(android.R.id.statusBarBackground);
                    if (navBar != null) {
                        names.add(navBar.getTransitionName());
                        sharedElements.put(navBar.getTransitionName(), navBar);
                    }
                    if (statusBar != null) {
                        names.add(statusBar.getTransitionName());
                        sharedElements.put(statusBar.getTransitionName(), statusBar);
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            postponeEnterTransition();
            Transition transition = TransitionInflater.from(this).inflateTransition(R.transition.shared_element);
            getWindow().setSharedElementEnterTransition(transition);
            setExitSharedElementCallback(mCallbackExit);
        }

        setContentView(R.layout.activity_article_list);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        if (mSwipeRefreshLayout!=null) {

            mSwipeRefreshLayout.setColorSchemeResources(android.R.color.holo_orange_light,
                    android.R.color.holo_green_light,
                    android.R.color.holo_blue_light,
                    android.R.color.holo_red_light);

            mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    refresh();
                }
            });
        }

        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        getLoaderManager().initLoader(0, null, this);

        if (savedInstanceState == null) {
            refresh();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mRefreshingReceiver,
                new IntentFilter(UpdaterService.BROADCAST_ACTION_STATE_CHANGE));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mRefreshingReceiver);
    }

    @Override
    public void onActivityReenter(int resultCode, Intent data) {
        super.onActivityReenter(resultCode, data);
        mReenterPositions = data.getExtras();
        mCurrentPosition = mReenterPositions.getInt(ArticleDetailActivity.EXTRA_CURRENT_ITEM_POSITION);
        int startPosition = mReenterPositions.getInt(ArticleDetailActivity.EXTRA_STARTING_ITEM_POSITION);
        if (startPosition != mCurrentPosition) {
            mRecyclerView.scrollToPosition(mCurrentPosition);
            if (Build.VERSION.SDK_INT >= 21) {
                postponeEnterTransition();
                mIsPostponed = true;
            }
        }

        mRecyclerView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mRecyclerView.getViewTreeObserver().removeOnPreDrawListener(this);

                mRecyclerView.requestLayout();
                if (Build.VERSION.SDK_INT >= 21 && mCursor !=null) {
                    mIsPostponed = false;
                    startPostponedEnterTransition();
                }
                return true;
            }
        });
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newAllArticlesInstance(this);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        mCursor = cursor;

        Adapter adapter = new Adapter(cursor);
        adapter.setHasStableIds(true);
        mRecyclerView.setAdapter(adapter);
        int columnCount = getResources().getInteger(R.integer.list_column_count);
        StaggeredGridLayoutManager sglm =
                new StaggeredGridLayoutManager(columnCount, StaggeredGridLayoutManager.VERTICAL);
        mRecyclerView.setLayoutManager(sglm);

        if (Build.VERSION.SDK_INT >= 21 && mIsPostponed) {
            mIsPostponed = false;
            startPostponedEnterTransition();
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mRecyclerView.setAdapter(null);
    }

    private void refresh() {
        startService(new Intent(this, UpdaterService.class));
    }

    private void updateRefreshingUI() {
        mSwipeRefreshLayout.setRefreshing(mIsRefreshing);
    }



    private class Adapter extends RecyclerView.Adapter<ViewHolder> {
        private Cursor mCursor;
        private Bundle mBundle;

        public Adapter(Cursor cursor) {
            mCursor = cursor;
        }

        @Override
        public long getItemId(int position) {
            mCursor.moveToPosition(position);
            return mCursor.getLong(ArticleLoader.Query._ID);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.list_item_article, parent, false);
            final ViewHolder vh = new ViewHolder(view);

            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    mBundle = null;
                    Intent intent = new Intent(Intent.ACTION_VIEW, ItemsContract.Items.buildItemUri(getItemId(vh.getAdapterPosition())));

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {

                        intent.putExtra(ArticleDetailActivity.EXTRA_STARTING_ITEM_POSITION, vh.getAdapterPosition());

                        String transitionName = String.valueOf(vh.getItemId()+"_"+vh.getAdapterPosition());
                        view.findViewById(R.id.thumbnail).setTransitionName(transitionName);

                        mBundle = ActivityOptions.makeSceneTransitionAnimation(ArticleListActivity.this, view.findViewById(R.id.thumbnail), view.findViewById(R.id.thumbnail).getTransitionName()).toBundle();
                    }

                    startActivity(intent, mBundle);
                }
            });
            return vh;
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            mCursor.moveToPosition(position);
            holder.titleView.setText(mCursor.getString(ArticleLoader.Query.TITLE));
            holder.subtitleView.setText(
                    DateUtils.getRelativeTimeSpanString(
                            mCursor.getLong(ArticleLoader.Query.PUBLISHED_DATE),
                            System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_ALL).toString()
                            + " by "
                            + mCursor.getString(ArticleLoader.Query.AUTHOR));
            holder.thumbnailView.setImageUrl(
                    mCursor.getString(ArticleLoader.Query.THUMB_URL),
                    ImageLoaderHelper.getInstance(ArticleListActivity.this).getImageLoader());
            holder.thumbnailView.setAspectRatio(mCursor.getFloat(ArticleLoader.Query.ASPECT_RATIO));
        }

        @Override
        public int getItemCount() {
            return mCursor.getCount();
        }
    }



    public static class ViewHolder extends RecyclerView.ViewHolder {
        public DynamicHeightNetworkImageView thumbnailView;
        public TextView titleView;
        public TextView subtitleView;

        public ViewHolder(View view) {
            super(view);
            thumbnailView = (DynamicHeightNetworkImageView) view.findViewById(R.id.thumbnail);
            titleView = (TextView) view.findViewById(R.id.article_title);
            subtitleView = (TextView) view.findViewById(R.id.article_subtitle);
        }
    }
}
