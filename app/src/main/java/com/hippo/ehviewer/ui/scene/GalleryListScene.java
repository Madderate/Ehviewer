/*
 * Copyright (C) 2015 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.ui.scene;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.hippo.animation.SimpleAnimatorListener;
import com.hippo.conaco.Conaco;
import com.hippo.drawable.AddDeleteDrawable;
import com.hippo.effect.ViewTransition;
import com.hippo.ehviewer.Constants;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.EhCacheKeyFactory;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.EhClient;
import com.hippo.ehviewer.client.EhRequest;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.GalleryListParser;
import com.hippo.ehviewer.client.PopularParser;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.client.data.ListUrlBuilder;
import com.hippo.ehviewer.client.data.QuickSearch;
import com.hippo.ehviewer.client.data.UnsupportedSearchException;
import com.hippo.ehviewer.ui.ContentActivity;
import com.hippo.ehviewer.util.DBUtils;
import com.hippo.ehviewer.util.EhUtils;
import com.hippo.ehviewer.util.Settings;
import com.hippo.ehviewer.widget.ContentLayout;
import com.hippo.ehviewer.widget.ListSearchBar;
import com.hippo.ehviewer.widget.LoadImageView;
import com.hippo.ehviewer.widget.OffsetLayout;
import com.hippo.ehviewer.widget.SearchBar;
import com.hippo.ehviewer.widget.SearchDatabase;
import com.hippo.ehviewer.widget.SearchLayout;
import com.hippo.ehviewer.widget.SimpleRatingView;
import com.hippo.rippleold.RippleSalon;
import com.hippo.scene.Announcer;
import com.hippo.scene.Curtain;
import com.hippo.scene.ElementsSharedCurtain;
import com.hippo.scene.Scene;
import com.hippo.scene.SimpleDialog;
import com.hippo.util.AnimationUtils;
import com.hippo.widget.FabLayout;
import com.hippo.widget.FloatingActionButton;
import com.hippo.widget.recyclerview.EasyRecyclerView;
import com.hippo.yorozuya.AssertUtils;
import com.hippo.yorozuya.MathUtils;
import com.hippo.yorozuya.Messenger;
import com.hippo.yorozuya.SimpleHandler;
import com.hippo.yorozuya.ViewUtils;

import java.util.List;

// TODO remeber the data in ContentHelper after screen dirction change
// TODO Must refresh when change source
// TODO disable click action when animating
// TODO Dim when expand search list
public final class GalleryListScene extends Scene implements ListSearchBar.Helper,
        View.OnClickListener, FabLayout.OnCancelListener,
        SearchLayout.SearhLayoutHelper, EasyRecyclerView.OnItemClickListener,
        Messenger.Receiver, DrawerProvider {

    public static final String KEY_MODE = "mode";

    public static final int MODE_HOMEPAGE = 0;
    public static final int MODE_POPULAR = 1;

    private static final long ANIMATE_TIME = 300l;

    private final static int STATE_NORMAL = 0;
    private final static int STATE_SIMPLE_SEARCH = 1;
    private final static int STATE_SEARCH = 2;
    private final static int STATE_SEARCH_SHOW_LIST = 3;

    private final static int FAB_STATE_NORMAL = 0;
    private final static int FAB_STATE_SEARCH = 1;

    private static final int BACK_PRESSED_INTERVAL = 2000;

    // Double click back exit
    private long mPressBackTime = 0;

    private ContentActivity mActivity;
    private Resources mResources;
    private SearchDatabase mSearchDatabase;

    private ListSearchBar mSearchBar;
    private ContentLayout mContentLayout;
    private EasyRecyclerView mContentRecyclerView;
    private SearchLayout mSearchLayout;
    private FabLayout mFabLayout;
    private FloatingActionButton mCornerFab;
    private FloatingActionButton mRefreshFab;
    private FloatingActionButton mGoToFab;

    private ViewTransition mViewTransition;

    private GalleryListHelper mGalleryListHelper;

    private int mSearchBarOriginalTop;
    private int mSearchBarOriginalBottom;
    private ValueAnimator mSearchBarMoveAnimator;

    private int mCornerFabOriginalBottom;
    private AddDeleteDrawable mAddDeleteDrawable;
    private Drawable mSearchDrawable;

    private int mState = STATE_NORMAL;
    private int mFabState = FAB_STATE_NORMAL;

    private ListUrlBuilder mListUrlBuilder = new ListUrlBuilder();

    private int mDrawerListActivatedPosition;

    private boolean mJpnTitle;


    private ViewGroup mRightDrawerView;
    private View mQuickSearchSettings;
    private EasyRecyclerView mQuickSearchView;
    private QuickSearchAdapter mQuickSearchAdapter;
    private List<QuickSearch> mQuickSearches;

    private SimpleDialog.OnCreateCustomViewListener mGoToCreateCustomViewListener =
            new SimpleDialog.OnCreateCustomViewListener() {
                @Override
                public void onCreateCustomView(final SimpleDialog dialog, View view) {
                    int currentPage = mGalleryListHelper.getCurrentPage();
                    int pageSize = mGalleryListHelper.getPageCount();
                    TextView pageInfo = (TextView) view.findViewById(R.id.go_to_page_info);
                    pageInfo.setText(String.format(mResources.getString(R.string.go_to_page_info),
                            currentPage + 1, pageSize == Integer.MAX_VALUE ?
                                    mResources.getString(R.string.unknown).toLowerCase() : Integer.toString(pageSize)));

                    EditText goToPage = (EditText) view.findViewById(R.id.go_to_page);
                    goToPage.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                        @Override
                        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_NULL) {
                                dialog.pressPositiveButton();
                                return true;
                            }
                            return false;
                        }
                    });
                }
            };

    private SimpleDialog.OnClickListener mGoToButtonClickListener =
            new SimpleDialog.OnClickListener() {
                @Override
                public boolean onClick(SimpleDialog dialog, int which) {
                    if (which == SimpleDialog.POSITIVE) {
                        int targetPage;
                        EditText goToPage = (EditText) dialog.findViewById(R.id.go_to_page);
                        try {
                            String text = goToPage.getText().toString();
                            if (TextUtils.isEmpty(text)) {
                                Toast.makeText(mActivity, R.string.go_to_error_null,
                                        Toast.LENGTH_SHORT).show();
                                return false;
                            }
                            targetPage = Integer.parseInt(text);
                            int pageSize = mGalleryListHelper.getPageCount();
                            if (targetPage <= 0 || (targetPage > pageSize)) {
                                Toast.makeText(mActivity, R.string.go_to_error_invaild,
                                        Toast.LENGTH_SHORT).show();
                                return false;
                            } else {
                                mGalleryListHelper.goTo(targetPage - 1);
                                return true;
                            }
                        } catch (NumberFormatException e) {
                            Toast.makeText(mActivity, R.string.go_to_error_not_number,
                                    Toast.LENGTH_SHORT).show();
                            return false;
                        }
                    } else {
                        return true;
                    }
                }
            };

    @Override
    public int getLaunchMode() {
        return LAUNCH_MODE_SINGLE_TOP;
    }

    private void handleAnnouncer(Announcer announcer) {
        int mode = MODE_HOMEPAGE;
        if (announcer != null) {
            mode = announcer.getIntExtra(KEY_MODE, MODE_HOMEPAGE);
        }

        switch (mode) {
            default:
            case MODE_HOMEPAGE:
                mDrawerListActivatedPosition = ContentActivity.DRAWER_LIST_HOMEPAGE;
                mActivity.setDrawerListActivatedPosition(ContentActivity.DRAWER_LIST_HOMEPAGE);
                mListUrlBuilder.reset();
                break;
            case MODE_POPULAR:
                mDrawerListActivatedPosition = ContentActivity.DRAWER_LIST_WHATS_HOT;
                mActivity.setDrawerListActivatedPosition(ContentActivity.DRAWER_LIST_WHATS_HOT);
                mListUrlBuilder.setMode(ListUrlBuilder.MODE_POPULAR);
                break;
        }

        if (announcer == null) {
            mGalleryListHelper.firstRefresh();
        } else {
            showSearchBar(true);
            mGalleryListHelper.refresh();
        }

        setState(STATE_NORMAL);
    }

    @Override
    protected void onNewAnnouncer(Announcer announcer) {
        super.onNewAnnouncer(announcer);

        handleAnnouncer(announcer);
    }

    @Override
    protected void onRebirth() {
        super.onRebirth();

        mGalleryListHelper = new GalleryListHelper(getStageActivity(), mGalleryListHelper);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(boolean rebirth) {
        super.onCreate(rebirth);
        setContentView(R.layout.scene_gallery_list);

        mActivity = (ContentActivity) getStageActivity();
        mResources = mActivity.getResources();
        mSearchDatabase = SearchDatabase.getInstance(getStageActivity());

        mSearchBar = (ListSearchBar) findViewById(R.id.search_bar);
        mContentLayout = (ContentLayout) findViewById(R.id.content_layout);
        mContentRecyclerView = mContentLayout.getRecyclerView();
        mSearchLayout = (SearchLayout) findViewById(R.id.search_layout);
        mFabLayout = (FabLayout) findViewById(R.id.fab_layout);
        mCornerFab = mFabLayout.getPrimaryFab();
        AssertUtils.assertEquals("FabLayout in GalleryListScene should contain " +
                "two secondary fab.", mFabLayout.getSecondaryFabCount(), 2);
        mRefreshFab = mFabLayout.getSecondaryFabAt(0);
        mGoToFab = mFabLayout.getSecondaryFabAt(1);

        mViewTransition = new ViewTransition(mContentLayout, mSearchLayout);

        // Init
        mSearchBar.setHelper(this);
        ViewUtils.measureView(mSearchBar, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        SearchBarMoveHelper sbmHelper = new SearchBarMoveHelper();
        mContentRecyclerView.addOnScrollListener(sbmHelper);
        mSearchLayout.addOnScrollListener(sbmHelper);
        mSearchLayout.setAction1Text(mResources.getString(R.string.search_add));
        mSearchLayout.setAction2Text(mResources.getString(R.string.search_mode));
        mSearchLayout.setHelper(this);

        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) mSearchBar.getLayoutParams();
        ViewUtils.measureView(mSearchBar, 200, ViewGroup.LayoutParams.WRAP_CONTENT);
        int fitPaddingTop = mSearchBar.getMeasuredHeight() + lp.topMargin;
        mContentLayout.setFitPaddingTop(fitPaddingTop);
        mSearchLayout.setFitPaddingTop(fitPaddingTop);
        mSearchBarOriginalTop = lp.topMargin;
        mSearchBarOriginalBottom = lp.topMargin + mSearchBar.getMeasuredHeight();


        /*
        mSearchBar.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                ViewUtils.removeOnGlobalLayoutListener(mSearchBar.getViewTreeObserver(), this);
                mSearchBarOriginalTop = mSearchBar.getTop();
                mSearchBarOriginalBottom = mSearchBar.getBottom();
                int fitPaddingTop = mSearchBar.getHeight() + mResources.getDimensionPixelOffset(R.dimen.search_bar_padding_vertical);
                mContentLayout.setFitPaddingTop(fitPaddingTop);
                mSearchLayout.setFitPaddingTop(fitPaddingTop);

                Log.d("TAG", "fitPaddingTop = " + fitPaddingTop);
                Log.d("TAG", "mSearchBarOriginalTop = " + mSearchBarOriginalTop);
                Log.d("TAG", "mSearchBarOriginalBottom = " + mSearchBarOriginalBottom);
            }
        });
        */



        // Content Layout
        // onRebirth may create new GalleryListHelper
        if (mGalleryListHelper == null) {
            mGalleryListHelper = new GalleryListHelper(mActivity);
        }

        mContentLayout.setHelper(mGalleryListHelper);

        mContentRecyclerView.setOnItemClickListener(this);

        // Fab Layout
        mFabLayout.setOnCancelListener(this);

        // Secondary Fab
        mRefreshFab.setOnClickListener(this);
        mGoToFab.setOnClickListener(this);

        // Corner Fab
        mCornerFab.setOnClickListener(this);
        mCornerFabOriginalBottom = mFabLayout.getPaddingBottom();
        mAddDeleteDrawable = new AddDeleteDrawable(getStageActivity());
        mAddDeleteDrawable.setColor(mResources.getColor(R.color.primary_drawable_dark));
        mSearchDrawable = mResources.getDrawable(R.drawable.ic_search_dark);
        mCornerFab.setDrawable(mAddDeleteDrawable);

        mRightDrawerView = (ViewGroup) getStageActivity().getLayoutInflater().inflate(R.layout.drawer_right_gallery_list, null);
        mQuickSearchSettings = mRightDrawerView.findViewById(R.id.settings);
        RippleSalon.addRipple(mQuickSearchSettings, true);
        mQuickSearchSettings.setOnClickListener(this);
        TextView rightDrawerTitle = (TextView) mRightDrawerView.findViewById(R.id.drawer_right_title);
        rightDrawerTitle.setText(R.string.quick_search);
        mQuickSearchView = (EasyRecyclerView) mRightDrawerView.findViewById(R.id.drawer_right_recycler_view);
        mQuickSearches = DBUtils.getAllQuickSearch();
        mQuickSearchAdapter = new QuickSearchAdapter();
        mQuickSearchView.setAdapter(mQuickSearchAdapter);
        mQuickSearchView.setLayoutManager(new LinearLayoutManager(getStageActivity()));
        mQuickSearchView.setSelector(RippleSalon.generateRippleDrawable(false));
        mQuickSearchView.setDrawSelectorOnTop(true);
        mQuickSearchView.setOnItemClickListener(new QuickSearchClickListener());
        mQuickSearchView.hasFixedSize();
        mQuickSearchView.setClipToPadding(false);

        // When scene start
        if (!rebirth) {
            handleAnnouncer(getAnnouncer());
        }
    }

    @Override
    protected void onRestore() {
        super.onRestore();

        int state = mState;
        // Reset state for set state
        mState = STATE_NORMAL;
        mFabState = FAB_STATE_NORMAL;
        setState(state, false);
    }

    @Override
    protected void onInit() {
        mJpnTitle = Settings.getJpnTitle();
        Messenger.getInstance().register(Constants.MESSENGER_ID_JPN_TITLE, this);
        Messenger.getInstance().register(Constants.MESSENGER_ID_UPDATE_QUICK_SEARCH, this);
    }

    @Override
    protected void onDie() {
        super.onDie();

        Messenger.getInstance().unregister(Constants.MESSENGER_ID_JPN_TITLE, this);
        Messenger.getInstance().unregister(Constants.MESSENGER_ID_UPDATE_QUICK_SEARCH, this);

        // Avoid memory leak
        if (mGalleryListHelper.mListener != null) {
            mGalleryListHelper.mListener.setGalleryListHelper(null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        ((ContentActivity) getStageActivity()).setDrawerListActivatedPosition(mDrawerListActivatedPosition);
    }

    @Override
    protected void onGetFitPaddingBottom(int b) {
        // Content Layout
        mContentLayout.setFitPaddingBottom(b);
        // Search Layout
        mSearchLayout.setFitPaddingBottom(b);
        // Corner Fab
        mFabLayout.setPadding(mFabLayout.getPaddingLeft(), mFabLayout.getPaddingTop(), mFabLayout.getPaddingRight(), mCornerFabOriginalBottom + b);
        mQuickSearchView.setPadding(0, 0, 0, b);
    }

    @Override
    public boolean showLeftDrawer() {
        return true;
    }

    @Override
    public void bindRightDrawer(ContentActivity activity) {
        activity.setRightDrawerView(mRightDrawerView);
    }

    private void setFabState(int fabState, boolean animation) {
        if (mFabState != fabState) {
            mFabState = fabState;
            Drawable drawable;
            if (mFabState == FAB_STATE_NORMAL) {
                drawable = mAddDeleteDrawable;
            } else if (mFabState == FAB_STATE_SEARCH) {
                drawable = mSearchDrawable;
            } else {
                return;
            }
            if (animation) {
                PropertyValuesHolder scaleXPvh = PropertyValuesHolder.ofFloat("scaleX", 1f, 0f);
                PropertyValuesHolder scaleYPvh = PropertyValuesHolder.ofFloat("scaleY", 1f, 0f);
                ObjectAnimator oa = ObjectAnimator.ofPropertyValuesHolder(mCornerFab, scaleXPvh, scaleYPvh);
                oa.setDuration(ANIMATE_TIME / 2);
                oa.setInterpolator(AnimationUtils.SLOW_FAST_INTERPOLATOR);
                oa.setRepeatCount(1);
                oa.setRepeatMode(ValueAnimator.REVERSE);
                final Drawable finalDrawable = drawable;
                oa.addListener(new SimpleAnimatorListener() {
                    @Override
                    public void onAnimationRepeat(Animator animation) {
                        mCornerFab.setDrawable(finalDrawable);
                    }
                });
                oa.start();
            } else {
                mCornerFab.setDrawable(drawable);
            }
        }
    }

    private void setState(int state) {
        setState(state, true);
    }

    private void setState(int state, boolean animation) {
        if (mState != state) {
            int oldState = mState;
            mState = state;

            switch (oldState) {
                case STATE_NORMAL:
                    switch (state) {
                        case STATE_SIMPLE_SEARCH:
                            mSearchBar.setState(SearchBar.STATE_SEARCH_LIST, animation);
                            returnSearchBarPosition();
                            setFabState(FAB_STATE_SEARCH, animation);
                            break;
                        case STATE_SEARCH:
                            mViewTransition.showView(1, animation);
                            mSearchLayout.scrollSearchContainerToTop();
                            mSearchBar.setState(SearchBar.STATE_SEARCH, animation);
                            returnSearchBarPosition();
                            setFabState(FAB_STATE_SEARCH, animation);
                            break;
                        case STATE_SEARCH_SHOW_LIST:
                            mViewTransition.showView(1, animation);
                            mSearchLayout.scrollSearchContainerToTop();
                            mSearchBar.setState(SearchBar.STATE_SEARCH_LIST, animation);
                            returnSearchBarPosition();
                            setFabState(FAB_STATE_SEARCH, animation);
                            break;
                    }
                    break;
                case STATE_SIMPLE_SEARCH:
                    switch (state) {
                        case STATE_NORMAL:
                            mSearchBar.setState(SearchBar.STATE_NORMAL, animation);
                            returnSearchBarPosition();
                            setFabState(FAB_STATE_NORMAL, animation);
                            break;
                        case STATE_SEARCH:
                            mViewTransition.showView(1, animation);
                            mSearchLayout.scrollSearchContainerToTop();
                            mSearchBar.setState(SearchBar.STATE_SEARCH, animation);
                            returnSearchBarPosition();
                            break;
                        case STATE_SEARCH_SHOW_LIST:
                            mViewTransition.showView(1, animation);
                            mSearchLayout.scrollSearchContainerToTop();
                            mSearchBar.setState(SearchBar.STATE_SEARCH_LIST, animation);
                            returnSearchBarPosition();
                            break;
                    }
                    break;
                case STATE_SEARCH:
                    switch (state) {
                        case STATE_NORMAL:
                            mViewTransition.showView(0, animation);
                            mSearchBar.setState(SearchBar.STATE_NORMAL, animation);
                            returnSearchBarPosition();
                            setFabState(FAB_STATE_NORMAL, animation);
                            break;
                        case STATE_SIMPLE_SEARCH:
                            mViewTransition.showView(0, animation);
                            mSearchBar.setState(SearchBar.STATE_SEARCH_LIST, animation);
                            returnSearchBarPosition();
                            break;
                        case STATE_SEARCH_SHOW_LIST:
                            mSearchBar.setState(SearchBar.STATE_SEARCH_LIST, animation);
                            returnSearchBarPosition();
                            break;
                    }
                    break;
                case STATE_SEARCH_SHOW_LIST:
                    switch (state) {
                        case STATE_NORMAL:
                            mViewTransition.showView(0, animation);
                            mSearchBar.setState(SearchBar.STATE_NORMAL, animation);
                            returnSearchBarPosition();
                            setFabState(FAB_STATE_NORMAL, animation);
                            break;
                        case STATE_SIMPLE_SEARCH:
                            mViewTransition.showView(0, animation);
                            mSearchBar.setState(SearchBar.STATE_SEARCH_LIST, animation);
                            returnSearchBarPosition();
                            break;
                        case STATE_SEARCH:
                            mSearchBar.setState(SearchBar.STATE_SEARCH, animation);
                            returnSearchBarPosition();
                            break;
                    }
                    break;
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mFabLayout.isExpanded()) {
            mFabLayout.setExpanded(false);
            mAddDeleteDrawable.setShape(false, ANIMATE_TIME);
        } else {
            switch (mState) {
                case STATE_NORMAL:
                    long time = System.currentTimeMillis();
                    if (getSceneCount() == 1 && time - mPressBackTime > BACK_PRESSED_INTERVAL) {
                        // It is the last scene
                        mPressBackTime = time;
                        Toast.makeText(getStageActivity(), "Press twice to exit", Toast.LENGTH_SHORT).show();
                    } else {
                        super.onBackPressed();
                    }
                    break;
                case STATE_SIMPLE_SEARCH:
                    setState(STATE_NORMAL);
                    break;
                case STATE_SEARCH:
                    setState(STATE_NORMAL);
                    break;
                case STATE_SEARCH_SHOW_LIST:
                    setState(STATE_SEARCH);
                    break;
            }
        }
    }

    private void toggleFabLayout() {
        mFabLayout.toggle();
        mAddDeleteDrawable.setShape(mFabLayout.isExpanded(), ANIMATE_TIME);
    }

    public void showGoToDialog() {
        int[] center = new int[2];
        ViewUtils.getCenterInAncestor(mGoToFab, center, R.id.stage);
        new SimpleDialog.Builder(getStageActivity()).setTitle(R.string._goto)
                .setCustomView(R.layout.dialog_go_to, mGoToCreateCustomViewListener)
                .setOnButtonClickListener(mGoToButtonClickListener)
                .setPositiveButton(android.R.string.ok)
                .setStartPoint(center[0], center[1]).show(this);
    }

    @Override
    public void onClick(View v) {
        if (v == mCornerFab) {
            if (mFabState == FAB_STATE_NORMAL) {
                toggleFabLayout();
            } else if (mFabState == FAB_STATE_SEARCH) {
                onApplySearch(mSearchBar.getText());
            }
        } else if (v == mRefreshFab) {
            toggleFabLayout();
            mGalleryListHelper.refreshWithSameSearch();
        } else if (v == mGoToFab) {
            toggleFabLayout();
            if (mGalleryListHelper.canGoTo()) {
                showGoToDialog();
            }
        } else if (v == mQuickSearchSettings) {
            startScene(QuickSearchScene.class);
        }
    }

    @Override
    public void onCancel(FabLayout fabLayout) {
        mAddDeleteDrawable.setAdd(ANIMATE_TIME);
    }

    @Override
    public void onClickTitle() {
        if (mState == STATE_NORMAL) {
            setState(STATE_SIMPLE_SEARCH);
        }
    }

    @Override
    public void onClickMenu() {
        mActivity.toggleDrawer();
    }

    @Override
    public void onClickArrow() {
        onBackPressed();
    }

    @Override
    public void onClickAdvanceSearch() {
        if (mState == STATE_NORMAL) {
            setState(STATE_SEARCH);
        }
    }

    @Override
    public void onSearchEditTextClick() {
        if (mState == STATE_SEARCH) {
            setState(STATE_SEARCH_SHOW_LIST);
        }
    }

    @Override
    public void onApplySearch(String query) {
        // TODO check is source and search vaild
        if (mViewTransition.getShownViewIndex() == 0 && TextUtils.isEmpty(query)) {
            return;
        }

        if (mViewTransition.getShownViewIndex() == 1) {
            int searchMode = mSearchLayout.getSearchMode();
            if (searchMode == SearchLayout.SEARCH_MODE_NORMAL &&
                    mSearchLayout.isSpecifyTag() && TextUtils.isEmpty(query)) {
                return;
            } else if (searchMode == SearchLayout.SEARCH_MODE_SPECIFY) {
                return;
            }
        }

        mDrawerListActivatedPosition = ContentActivity.DRAWER_LIST_NONE;
        mActivity.setDrawerListActivatedPosition(ContentActivity.DRAWER_LIST_NONE);

        mSearchDatabase.addQuery(query);

        if (mViewTransition.getShownViewIndex() == 0) {
            mListUrlBuilder.reset();
            mListUrlBuilder.setKeyword(query);
        } else {
            mSearchLayout.formatListUrlBuilder(mListUrlBuilder);
            if (mSearchLayout.isSpecifyTAuthor()) {
                query = "uploader:" + query;
            }
            mListUrlBuilder.setKeyword(query);
        }

        setState(STATE_NORMAL);

        showSearchBar(true);
        mGalleryListHelper.refresh();
    }

    private RecyclerView getVaildRecyclerView() {
        if (mState == STATE_NORMAL || mState == STATE_SIMPLE_SEARCH) {
            return mContentRecyclerView;
        } else {
            return mSearchLayout;
        }
    }

    private void showSearchBar(boolean animation) {
        if (mSearchBar.isLayoutRequested()) {
            return;
        }

        // Cancel old animator
        if (mSearchBarMoveAnimator != null) {
            mSearchBarMoveAnimator.cancel();
        }

        int offset = mSearchBarOriginalTop - mSearchBar.getTop();
        if (offset != 0) {
            if (animation) {
                final boolean needRequestLayout = mSearchBar.getBottom() <= 0 && offset > 0;
                final ValueAnimator va = ValueAnimator.ofInt(0, offset);
                va.setDuration(ANIMATE_TIME);
                va.addListener(new SimpleAnimatorListener() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mSearchBarMoveAnimator = null;
                    }
                });
                va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    int lastValue;
                    boolean hasRequestLayout;
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        int value = (Integer) animation.getAnimatedValue();
                        int offsetStep = value - lastValue;
                        lastValue = value;
                        mSearchBar.offsetTopAndBottom(offsetStep);
                        ((OffsetLayout.LayoutParams) mSearchBar.getLayoutParams()).offsetY += offsetStep;

                        if (!hasRequestLayout && needRequestLayout && mSearchBar.getBottom() > 0) {
                            hasRequestLayout = true;
                            mSearchBar.requestLayout();
                        }
                    }
                });
                if (mSearchBarMoveAnimator != null) {
                    mSearchBarMoveAnimator.cancel();
                }
                mSearchBarMoveAnimator = va;
                va.start();
            } else {
                mSearchBar.offsetTopAndBottom(offset);
                ((OffsetLayout.LayoutParams) mSearchBar.getLayoutParams()).offsetY += offset;
            }
        }
    }

    private void returnSearchBarPosition() {
        if (mSearchBar.isLayoutRequested()) {
            return;
        }

        boolean show;
        if (mState == STATE_SIMPLE_SEARCH || mState == STATE_SEARCH_SHOW_LIST) {
            show = true;
        } else {
            RecyclerView recyclerView = getVaildRecyclerView();
            if (!recyclerView.isShown()) {
                show = true;
            } else if (recyclerView.computeVerticalScrollOffset() < mSearchBarOriginalBottom){
                show = true;
            } else {
                show = mSearchBar.getBottom() > (mSearchBarOriginalBottom - mSearchBarOriginalTop) / 2;
            }
        }

        int offset;
        if (show) {
            offset = mSearchBarOriginalTop - mSearchBar.getTop();
        } else {
            offset = -mSearchBar.getBottom();
        }

        if (offset != 0) {
            final boolean needRequestLayout = mSearchBar.getBottom() <= 0 && offset > 0;
            final ValueAnimator va = ValueAnimator.ofInt(0, offset);
            va.setDuration(ANIMATE_TIME);
            va.addListener(new SimpleAnimatorListener() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mSearchBarMoveAnimator = null;
                }
            });
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                int lastValue;
                boolean hasRequestLayout;
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    int value = (Integer) animation.getAnimatedValue();
                    int offsetStep = value - lastValue;
                    lastValue = value;
                    mSearchBar.offsetTopAndBottom(offsetStep);
                    ((OffsetLayout.LayoutParams) mSearchBar.getLayoutParams()).offsetY += offsetStep;

                    if (!hasRequestLayout && needRequestLayout && mSearchBar.getBottom() > 0) {
                        hasRequestLayout = true;
                        mSearchBar.requestLayout();
                    }
                }
            });
            if (mSearchBarMoveAnimator != null) {
                mSearchBarMoveAnimator.cancel();
            }
            mSearchBarMoveAnimator = va;
            va.start();
        }
    }

    private class AddToQuickSearchHelper implements SimpleDialog.OnCreateCustomViewListener,
            SimpleDialog.OnClickListener {

        private EditText mEditText;
        private ListUrlBuilder mBuilder;

        public AddToQuickSearchHelper(ListUrlBuilder builder) {
            mBuilder = builder;
        }

        @Override
        public void onCreateCustomView(final SimpleDialog dialog, View view) {
            mEditText = (EditText) view.findViewById(R.id.edit_text);
            mEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) {
                        dialog.pressPositiveButton();
                        return true;
                    } else {
                        return false;
                    }
                }
            });
        }

        @Override
        public boolean onClick(SimpleDialog dialog, int which) {
            if (which == SimpleDialog.POSITIVE) {
                String displayname = mEditText.getText().toString();
                if (!TextUtils.isEmpty(displayname)) {
                    DBUtils.addQuickSearch(displayname, mBuilder);
                    // Notify
                    Messenger.getInstance().notify(Constants.MESSENGER_ID_UPDATE_QUICK_SEARCH, null);
                    return true;
                } else {
                    Toast.makeText(getStageActivity(), "Empty displayname", Toast.LENGTH_SHORT).show(); // TODO hardcode
                    return false;
                }
            } else {
                return true;
            }
        }
    }

    @Override
    public void onAction1() {
        if (mSearchLayout.getSearchMode() == SearchLayout.SEARCH_MODE_NORMAL) {
            ListUrlBuilder builder = new ListUrlBuilder();
            mSearchLayout.formatListUrlBuilder(builder);
            // Set keyword from search bar
            builder.setKeyword(mSearchBar.getText());
            AddToQuickSearchHelper helper = new AddToQuickSearchHelper(builder);
            new SimpleDialog.Builder(getStageActivity()).setTitle("Add to quick search") // TODO hardcode
                    .setCustomView(R.layout.dialog_add_to_quick_search, helper)
                    .setPositiveButton(android.R.string.ok)
                    .setOnButtonClickListener(helper).show(this);
        }
    }

    @Override
    public void onAction2() {
        mSearchLayout.toggleSearchMode();
    }

    @Override
    public void onChangeSearchMode() {
        showSearchBar(true);
    }

    @Override
    public void onRequestSelectImage() {
        // TODO
    }

    @Override
    public void onSpecifyGallery(int gid, String token) {
        Announcer announcer = new Announcer();
        announcer.setAction(GalleryDetailScene.ACTION_GID_TOKEN);
        announcer.putExtra(GalleryDetailScene.KEY_GID, gid);
        announcer.putExtra(GalleryDetailScene.KEY_TOKEN, token);
        startScene(GalleryDetailScene.class, announcer);
    }

    @Override
    public Scene getScene() {
        return this;
    }

    @Override
    public boolean onItemClick(EasyRecyclerView parent, View view, int position, long id) {
        GalleryInfo gi = mGalleryListHelper.getDataAt(position);
        Announcer announcer = new Announcer();
        announcer.setAction(GalleryDetailScene.ACTION_GALLERY_INFO);
        announcer.putExtra(GalleryDetailScene.KEY_GALLERY_INFO, gi);

        Curtain curtain = new ElementsSharedCurtain(new TransitonHelper(position));

        startScene(GalleryDetailScene.class, announcer, curtain);

        return true;
    }

    @Override
    public void onReceive(int id, Object obj) {
        if (Constants.MESSENGER_ID_JPN_TITLE == id) {
            mJpnTitle = (Boolean) obj;
        } else if (Constants.MESSENGER_ID_UPDATE_QUICK_SEARCH == id) {
            mQuickSearches = DBUtils.getAllQuickSearch();
            mQuickSearchAdapter.notifyDataSetChanged();
        }
    }

    private class TransitonHelper implements ElementsSharedCurtain.ViewPairSet {

        private int mPosition;

        public TransitonHelper(int position) {
            mPosition = position;
        }

        @Override
        public View[] getFromViewSet(@NonNull Scene fromScene) {
            View[] views = new View[4];
            GalleryListScene scene = (GalleryListScene) fromScene;
            RecyclerView.ViewHolder viewHolder = scene.mContentRecyclerView.findViewHolderForAdapterPosition(mPosition);
            if (viewHolder != null) {
                GalleryHolder gh = (GalleryHolder) viewHolder;
                views[0] = gh.thumb;
                views[1] = gh.title;
                views[2] = gh.uploader;
                views[3] = gh.category;
            }
            return views;
        }

        @Override
        public View[] getToViewSet(@NonNull Scene toScene) {
            View[] views = new View[4];
            GalleryDetailScene scene = (GalleryDetailScene) toScene;
            views[0] = scene.mThumb;
            views[1] = scene.mTitle;
            views[2] = scene.mUploader;
            views[3] = scene.mCategory;
            return views;
        }
    }

    private class SearchBarMoveHelper extends RecyclerView.OnScrollListener {

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState){
            if (newState == RecyclerView.SCROLL_STATE_IDLE && isVaildView(recyclerView)) {
                returnSearchBarPosition();
            }
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            if (isVaildView(recyclerView)) {

                int oldBottom = mSearchBar.getBottom();
                int offsetYStep = MathUtils.clamp(-dy, -mSearchBar.getBottom(), mSearchBarOriginalTop - mSearchBar.getTop());
                mSearchBar.offsetTopAndBottom(offsetYStep);
                ((OffsetLayout.LayoutParams) mSearchBar.getLayoutParams()).offsetY += offsetYStep;
                int newBottom = mSearchBar.getBottom();

                // Sometimes if it is out of screen than go into again, it do not show,
                // so I need to requestLayout
                if (oldBottom <= 0 && newBottom > 0) {
                    mSearchBar.requestLayout();
                }
            }
        }

        private boolean isVaildView(RecyclerView view) {
            return (mState == STATE_NORMAL && view == mContentRecyclerView) ||
                    (mState == STATE_SEARCH && view == mSearchLayout);
        }
    }

    private class GalleryHolder extends RecyclerView.ViewHolder {

        public LoadImageView thumb;
        public TextView title;
        public TextView uploader;
        public SimpleRatingView rating;
        public TextView category;
        public TextView posted;
        public TextView simpleLanguage;

        public GalleryHolder(View itemView) {
            super(itemView);
            thumb = (LoadImageView) itemView.findViewById(R.id.thumb);
            title = (TextView) itemView.findViewById(R.id.title);
            uploader = (TextView) itemView.findViewById(R.id.uploader);
            rating = (SimpleRatingView) itemView.findViewById(R.id.rating);
            category = (TextView) itemView.findViewById(R.id.category);
            posted = (TextView) itemView.findViewById(R.id.posted);
            simpleLanguage = (TextView) itemView.findViewById(R.id.simple_language);
        }
    }

    private class GalleryListHelper extends ContentLayout.ContentHelper<GalleryInfo, GalleryHolder> {

        private GalleryListHelperSettable mListener;

        private Runnable mSearchBarPositionTask = new Runnable() {
            @Override
            public void run() {
                returnSearchBarPosition();
            }
        };

        public GalleryListHelper(Context context) {
            super(context);
        }

        public GalleryListHelper(Context context, GalleryListHelper older) {
            super(context, older);
            // Update gallery listener
            mListener = older.mListener;
            if (mListener != null) {
                mListener.setGalleryListHelper(this);
                older.mListener = null;
            }
        }

        private void clearLastGalleryListHelperSettable(GalleryListHelperSettable listener) {
            if (mListener == listener) {
                mListener = null;
            }
        }

        @Override
        protected RecyclerView.LayoutManager generateLayoutManager() {
            // set SpanCount in runtime
            return new StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL);
        }

        @Override
        protected void onScrollToPosition() {
            SimpleHandler.getInstance().post(mSearchBarPositionTask);
        }

        @Override
        protected void onShowProgress() {
            showSearchBar(true);
        }

        @Override
        protected void onShowText() {
            showSearchBar(true);
        }

        @Override
        protected void getPageData(int taskId, int type, int page) {
            EhClient client = EhApplication.getEhClient(getStageActivity());

            int source = Settings.getEhSource();
            int mode = mListUrlBuilder.getMode();
            if (mode == ListUrlBuilder.MODE_POPULAR) {
                PopularListener listener = new PopularListener(taskId);
                listener.setGalleryListHelper(this);
                mListener = listener;
                EhRequest request = new EhRequest();
                request.setMethod(mJpnTitle ?
                        EhClient.METHOD_GET_POPULAR_JPN : EhClient.METHOD_GET_POPULAR);
                request.setEhListener(listener);
                request.setArgs(source);
                client.execute(request);
            } else {
                try {
                    mListUrlBuilder.setPageIndex(page);
                    String url =  mListUrlBuilder.build(source);
                    GalleryListListener listener = new GalleryListListener(taskId,
                            type, page, source);
                    listener.setGalleryListHelper(this);
                    mListener = listener;

                    EhRequest request = new EhRequest();
                    request.setMethod(mJpnTitle ?
                            EhClient.METHOD_GET_GALLERY_LIST_JPN : EhClient.METHOD_GET_GALLERY_LIST);
                    request.setEhListener(listener);
                    request.setArgs(url, source);

                    client.execute(request);
                } catch (UnsupportedSearchException e) {
                    onGetPageData(taskId, e);
                }
            }
        }

        @Override
        public GalleryHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = mActivity.getLayoutInflater().inflate(R.layout.item_gallery_list_detail, parent, false);
            return new GalleryHolder(view);
        }

        @Override
        public void onBindViewHolder(GalleryHolder holder, int position) {
            GalleryInfo gi = getDataAt(position);

            Conaco conaco = EhApplication.getConaco(getStageActivity());
            holder.thumb.load(conaco, EhCacheKeyFactory.getThumbKey(gi.gid), gi.thumb);
            String title;
            if (mJpnTitle) {
                title = TextUtils.isEmpty(gi.titleJpn) ? gi.title : gi.titleJpn;
            } else {
                title = gi.title;
            }
            holder.title.setText(title);
            holder.uploader.setText(gi.uploader);
            holder.rating.setRating(gi.rating);
            TextView category = holder.category;
            String newCategoryText = EhUtils.getCategory(gi.category);
            if (!newCategoryText.equals(category.getText())) {
                category.setText(newCategoryText);
                category.setBackgroundColor(EhUtils.getCategoryColor(gi.category));
            }
            holder.posted.setText(gi.posted);
            holder.simpleLanguage.setText(gi.simpleLanguage);
        }
    }

    private static class GalleryListListener extends EhClient.EhListener<GalleryListParser.Result>
            implements GalleryListHelperSettable {

        private int mTaskId;
        private int mTaskType;
        private int mTargetPage;
        private int mSource;

        private GalleryListHelper mHelper;

        public GalleryListListener(int taskId, int taskType, int targetPage, int source) {
            mTaskId = taskId;
            mTaskType = taskType;
            mTargetPage = targetPage;
            mSource = source;
        }

        @Override
        public void setGalleryListHelper(GalleryListHelper helper) {
            mHelper = helper;
        }

        @Override
        public void onSuccess(GalleryListParser.Result result) {
            if (mHelper != null) {
                if (mSource == EhUrl.SOURCE_LOFI) {
                    if (result.pages == GalleryListParser.Result.CURRENT_PAGE_IS_LAST) {
                        mHelper.setPageCount(mTargetPage);
                    } else if (mTaskType == ContentLayout.ContentHelper.TYPE_REFRESH) {
                        mHelper.setPageCount(Integer.MAX_VALUE);
                    }
                } else {
                    mHelper.setPageCount(result.pages);
                }
                mHelper.onGetPageData(mTaskId, result.galleryInfos);

                mHelper.clearLastGalleryListHelperSettable(this);
            }
        }

        @Override
        public void onFailure(Exception e) {
            if (mHelper != null) {
                mHelper.onGetPageData(mTaskId, e);

                mHelper.clearLastGalleryListHelperSettable(this);
            }
        }

        @Override
        public void onCanceled() {

        }
    }

    private static class PopularListener extends EhClient.EhListener<PopularParser.Result>
            implements GalleryListHelperSettable {

        private int mTaskId;
        private GalleryListHelper mHelper;

        public PopularListener(int taskId) {
            mTaskId = taskId;
        }

        @Override
        public void setGalleryListHelper(GalleryListHelper helper) {
            mHelper = helper;
        }

        @Override
        public void onSuccess(PopularParser.Result result) {
            if (mHelper != null) {
                mHelper.setPageCount(1);
                mHelper.onGetPageData(mTaskId, result.galleryInfos);

                mHelper.clearLastGalleryListHelperSettable(this);
            }
        }

        @Override
        public void onFailure(Exception e) {
            if (mHelper != null) {
                mHelper.onGetPageData(mTaskId, e);

                mHelper.clearLastGalleryListHelperSettable(this);
            }
        }

        @Override
        public void onCanceled() {

        }
    }

    private interface GalleryListHelperSettable {
        void setGalleryListHelper(GalleryListHelper helper);
    }

    private class SimpleHolder extends RecyclerView.ViewHolder {

        public SimpleHolder(View itemView) {
            super(itemView);
        }
    }

    private class QuickSearchAdapter extends RecyclerView.Adapter<SimpleHolder> {

        @Override
        public SimpleHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new SimpleHolder(getStageActivity().getLayoutInflater().inflate(
                    R.layout.item_quick_search, parent, false));
        }

        @Override
        public void onBindViewHolder(SimpleHolder holder, int position) {
            ((TextView) holder.itemView).setText(mQuickSearches.get(position).name);
        }

        @Override
        public int getItemCount() {
            return mQuickSearches.size();
        }
    }

    private class QuickSearchClickListener implements EasyRecyclerView.OnItemClickListener {
        @Override
        public boolean onItemClick(EasyRecyclerView parent, View view, int position, long id) {
            setState(STATE_NORMAL);
            mListUrlBuilder.set(mQuickSearches.get(position));
            showSearchBar(true);
            mGalleryListHelper.refresh();
            ((ContentActivity) getStageActivity()).closeDrawers();
            return true;
        }
    }
}
