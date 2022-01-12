/*
 * Copyright 2020 Mupceet
 * Copyright (C) 2013 The Android Open Source Project
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

package com.mupceet.dragmultiselect;

import android.content.res.Resources;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.OnItemTouchListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DragMultiSelectHelper is a utility class that based on {@link RecyclerView.OnItemTouchListener}
 * for adding selecting with automatic edge-triggered scrolling to RecyclerView.
 * <p>
 * <h1>Hotspot</h1> Automatic scrolling starts when the user moving to
 * an hotspot area. By default, hotspot areas are defined as the top/bottom or left/right
 * 20% of the host view's total area. Moving to the top hotspot area scrolls up,
 * left scrolls to the left, and so on.
 * <p>
 * As the user touches closer to the extreme edge of the hotspot area,
 * scrolling accelerates up to a maximum velocity. When using the default edge
 * type, {@link EdgeType#INSIDE_EXTEND}, moving outside of the view bounds
 * will scroll at the maximum velocity.
 * <p>
 * The following hotspot properties may be configured:
 * <ul>
 * <li>Location of hotspot areas, see {@link #setEdgeType(EdgeType)}. Default value is
 * {@link EdgeType#INSIDE_EXTEND}.
 * <li>Size of hotspot areas relative to view size, see
 * {@link #setRelativeHotspotEdges(float)}. Default value is 20% for both vertical and
 * horizontal edges.
 * <li>Maximum size used to constrain relative size, see
 * {@link #setMaximumHotspotEdges(float)}. Default value is {@link Float#MAX_VALUE}.
 * </ul>
 * <h1>Scrolling</h1> When automatic scrolling is active, the helper will
 * repeatedly call {@link RecyclerView#scrollBy(int, int)} to apply new scrolling offsets.
 * <p>
 * The following scrolling properties may be configured:
 * <ul>
 * <li>Target velocity relative to view size, see {@link #setRelativeVelocity}.
 * Default value is 100% per second for both vertical and horizontal.
 * <li>Minimum velocity used to constrain relative velocity, see
 * {@link #setMinimumVelocity}. When set, scrolling will accelerate to the
 * larger of either this value or the relative target value. Default value is
 * approximately 315 dips per second.
 * <li>Maximum velocity used to constrain relative velocity, see
 * {@link #setMaximumVelocity}. Default value is approximately 1575 dips per second.
 * </ul>
 */
public class DragMultiSelectHelper {
    public static final float NO_MAX = Float.MAX_VALUE;
    public static final float NO_MIN = 0;
    public static final float RELATIVE_UNSPECIFIED = 0;
    private static final int HORIZONTAL = RecyclerView.HORIZONTAL;
    private static final int VERTICAL = RecyclerView.VERTICAL;

    /*
     *                        !autoChangeMode           +-------------------+     inactiveSelect()
     *           +------------------------------------> |                   | <--------------------+
     *           |                                      |      Normal       |                      |
     *           |        activeDragSelect(position)    |                   | activeSlideSelect()  |
     *           |      +------------------------------ |                   | ----------+          |
     *           |      v                               +-------------------+           v          |
     *  +-------------------+                              autoChangeMode     +-----------------------+
     *  | Drag From Normal  | ----------------------------------------------> |                       |
     *  +-------------------+                                                 |                       |
     *  |                   |                                                 |                       |
     *  |                   | activeDragSelect(position) && allowDragInSlide  |        Slide          |
     *  |                   | <---------------------------------------------- |                       |
     *  |  Drag From Slide  |                                                 |                       |
     *  |                   |                                                 |                       |
     *  |                   | ----------------------------------------------> |                       |
     *  +-------------------+                                                 +-----------------------+
     */
    private static final int SELECT_STATE_NORMAL = 0x00;
    private static final int SELECT_STATE_SLIDE = 0x01;
    private static final int SELECT_STATE_DRAG_FROM_NORMAL = 0x10;
    private static final int SELECT_STATE_DRAG_FROM_SLIDE = 0x11;

    private static final int DEFAULT_MIN_VELOCITY_DP = 315;
    private static final int DEFAULT_MAX_VELOCITY_DP = 1575;
    private static final float DEFAULT_RELATIVE_VELOCITY = 1f;
    private static final float DEFAULT_MAX_EDGE = NO_MAX;
    private static final float DEFAULT_RELATIVE_EDGE = 0.2f;
    private static final EdgeType DEFAULT_EDGE_TYPE = EdgeType.INSIDE_EXTEND;

    private final AutoScroller mScroller = new AutoScroller(new AutoScroller.ScrollStateChangeListener() {
        @Override
        public void onScrollStateChange(boolean scroll) {
            if (scroll) {
                if (mRecyclerView == null) {
                    Logger.e("startAutoScroll：Host view has been cleared.");
                    return;
                }
                if (!mScroller.isScrolling()) {
                    Logger.d("mScrollRunnable post");
                    mRecyclerView.post(mScrollRunnable);
                }
            } else {
                if (mRecyclerView == null) {
                    Logger.e("stopAutoScroll：Host view has been cleared.");
                    return;
                }
                if (mScroller.isScrolling()) {
                    Logger.d("mScrollRunnable remove");
                    mRecyclerView.removeCallbacks(mScrollRunnable);
                }
                updateSelectedRange(mRecyclerView,
                        mLastTouchPosition[HORIZONTAL], mLastTouchPosition[VERTICAL]);
            }
        }
    });
    private final SelectionRecorder mSelectionRecorder = new SelectionRecorder();
    /**
     * Edge insets used to activate auto-scrolling.
     */
    private float mHotspotRelativeEdges = RELATIVE_UNSPECIFIED;
    /**
     * Clamping values for edge insets used to activate auto-scrolling.
     */
    private float mHotspotMaximumEdges = NO_MAX;
    /**
     * Relative scrolling velocity at maximum edge distance.
     */
    private float mRelativeVelocity = RELATIVE_UNSPECIFIED;
    /**
     * Clamping values used for scrolling velocity.
     */
    private float mMinimumVelocity = NO_MIN;
    /**
     * Clamping values used for scrolling velocity.
     */
    private float mMaximumVelocity = NO_MAX;
    /**
     * Developer callback which controls the behavior of DragSelectTouchHelper.
     */
    @NonNull
    private final Callback mCallback;
    private final float[] mLastTouchPosition = new float[]{Float.MIN_VALUE, Float.MIN_VALUE};
    private RecyclerView mRecyclerView = null;
    private int mDirection = VERTICAL;
    private final Runnable mScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (mRecyclerView == null) {
                Logger.i("mScrollRunnable run: Host view has been cleared.");
                return;
            }
            final AutoScroller scroller = mScroller;
            if (!scroller.isScrolling()) {
                return;
            }

            scrollBy(scroller.getDelta());
            ViewCompat.postOnAnimation(mRecyclerView, this);
        }
    };
    /**
     * Start of the slide area.
     */
    private float mSlideAreaStart;
    /**
     * End of the slide area.
     */
    private float mSlideAreaEnd;
    /**
     * The type of edge being used.
     */
    private EdgeType mEdgeType;
    /**
     * Whether should auto enter slide mode after drag select finished.
     */
    private boolean mShouldAutoChangeState;
    /**
     * Whether can drag selection in slide select mode.
     */
    private boolean mIsAllowDragInSlideState;
    /**
     * The current mode of selection.
     */
    private int mSelectState = SELECT_STATE_NORMAL;
    private int mSlideStateStartPosition = RecyclerView.NO_POSITION;
    private boolean mHaveCalledSelectStart = false;
    private final OnItemTouchListener mOnItemTouchListener = new OnItemTouchListener() {
        @Override
        public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
            RecyclerView.Adapter<?> adapter = rv.getAdapter();
            if (adapter == null || adapter.getItemCount() == 0) {
                return false;
            }
            boolean intercept = false;
            int action = e.getAction();
            int actionMask = action & MotionEvent.ACTION_MASK;
            // It seems that it's unnecessary to process multiple pointers.
            switch (actionMask) {
                case MotionEvent.ACTION_DOWN:
                    // call the selection start's callback before moving
                    if (mSelectState == SELECT_STATE_SLIDE && isInSlideArea(e)) {
                        mSlideStateStartPosition = getItemPosition(rv, e.getX(), e.getY());
                        if (mSlideStateStartPosition != RecyclerView.NO_POSITION) {
                            intercept = true;
                            mCallback.onSelectStart(mSlideStateStartPosition);
                            mHaveCalledSelectStart = true;
                        }
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (mSelectState == SELECT_STATE_DRAG_FROM_NORMAL
                            || mSelectState == SELECT_STATE_DRAG_FROM_SLIDE) {
                        Logger.i("onInterceptTouchEvent: move in drag mode");
                        intercept = true;
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                    // finger is lifted before moving
                    Logger.i("onInterceptTouchEvent: finger is lifted before moving");
                    // fall through
                case MotionEvent.ACTION_UP:
                    if (mSelectState == SELECT_STATE_DRAG_FROM_NORMAL
                            || mSelectState == SELECT_STATE_DRAG_FROM_SLIDE) {
                        intercept = true;
                    }
                    if (mSlideStateStartPosition != RecyclerView.NO_POSITION) {
                        selectFinished(mSlideStateStartPosition);
                        mSlideStateStartPosition = RecyclerView.NO_POSITION;
                    }
                    // selection has triggered
                    if (mSelectionRecorder.startPosition() != RecyclerView.NO_POSITION) {
                        selectFinished(mSelectionRecorder.endPosition());
                    }
                    break;
                default:
                    // do nothing
            }
            // Intercept only when the selection is triggered
            if (intercept) {
                Logger.i("Will intercept event");
            }
            return intercept;
        }

        @Override
        public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
            if (!isSelectActivated()) {
                Logger.i("onTouchEvent: not active");
                return;
            }
            int action = e.getAction();
            int actionMask = action & MotionEvent.ACTION_MASK;
            switch (actionMask) {
                case MotionEvent.ACTION_MOVE:
                    if (mSlideStateStartPosition != RecyclerView.NO_POSITION) {
                        Logger.i("onTouchEvent: move after slide mode down");
                        selectFirstItem(mSlideStateStartPosition);
                        // selection is triggered
                        mSlideStateStartPosition = RecyclerView.NO_POSITION;
                    }
                    if (mDirection == HORIZONTAL) {
                        float downY = e.getY();
                        int paddingBottom = rv.getHeight() - rv.getPaddingBottom();
                        // we need Y position to find item.
                        if (downY < rv.getPaddingTop()) {
                            mLastTouchPosition[VERTICAL] = rv.getPaddingTop();
                        } else if (downY > paddingBottom) {
                            mLastTouchPosition[VERTICAL] = paddingBottom;
                        } else {
                            mLastTouchPosition[VERTICAL] = downY;
                        }
                        // it will record X position.
                        computeTargetVelocity(HORIZONTAL, e.getX(), rv.getWidth());
                    } else {
                        float downX = e.getX();
                        int paddingRight = rv.getWidth() - rv.getPaddingRight();
                        // we need X position to find item.
                        if (downX < rv.getPaddingLeft()) {
                            mLastTouchPosition[HORIZONTAL] = rv.getPaddingLeft();
                        } else if (downX > paddingRight) {
                            mLastTouchPosition[HORIZONTAL] = paddingRight;
                        } else {
                            mLastTouchPosition[HORIZONTAL] = downX;
                        }
                        // it will record Y position.
                        computeTargetVelocity(VERTICAL, e.getY(), rv.getHeight());
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    selectFinished(mSelectionRecorder.endPosition());
                    break;
                default:
                    // do nothing
            }
        }

        @Override
        public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            if (disallowIntercept) {
                inactiveSelect();
            }
        }
    };

    public DragMultiSelectHelper(@NonNull Callback callback) {
        mCallback = callback;
        DisplayMetrics mDisplayMetrics = Resources.getSystem().getDisplayMetrics();

        setEdgeType(DEFAULT_EDGE_TYPE);
        final int maxVelocity = (int) (DEFAULT_MAX_VELOCITY_DP * mDisplayMetrics.density + 0.5f);
        final int minVelocity = (int) (DEFAULT_MIN_VELOCITY_DP * mDisplayMetrics.density + 0.5f);
        setMaximumVelocity(maxVelocity);
        setMinimumVelocity(minVelocity);
        setMaximumHotspotEdges(DEFAULT_MAX_EDGE);
        setRelativeHotspotEdges(DEFAULT_RELATIVE_EDGE);
        setRelativeVelocity(DEFAULT_RELATIVE_VELOCITY);
        setAutoEnterSlideState(false);
        setAllowDragInSlideState(false);
        setSlideArea(0, 0);
    }

    /**
     * Attaches the DragSelectTouchHelper to the provided RecyclerView. If TouchHelper is already
     * attached to a RecyclerView, it will first detach from the previous one. You can call this
     * method with {@code null} to detach it from the current RecyclerView.
     *
     * @param recyclerView The RecyclerView instance to which you want to add this helper or
     *                     {@code null} if you want to remove DragSelectTouchHelper from the
     *                     current RecyclerView.
     */
    public void attachToRecyclerView(@Nullable RecyclerView recyclerView) {
        if (mRecyclerView == recyclerView) {
            return; // nothing to do
        }
        if (mRecyclerView != null) {
            mRecyclerView.removeOnItemTouchListener(mOnItemTouchListener);
        }
        mRecyclerView = recyclerView;
        if (mRecyclerView != null) {
            mRecyclerView.addOnItemTouchListener(mOnItemTouchListener);
        }
    }

    /**
     * Activate the slide selection mode.
     */
    public void activeSlideSelect() {
        activeSelectInternal(RecyclerView.NO_POSITION);
    }

    /**
     * Activate the selection mode with selected item position. Normally called on long press.
     *
     * @param position Indicates the position of selected item.
     */
    public void activeDragSelect(int position) {
        activeSelectInternal(position);
    }

    /**
     * Exit the selection mode.
     */
    public void inactiveSelect() {
        if (isSelectActivated()) {
            selectFinished(mSelectionRecorder.endPosition());
        } else {
            selectFinished(RecyclerView.NO_POSITION);
        }
        Logger.logSelectStateChange(mSelectState, SELECT_STATE_NORMAL);
        mSelectState = SELECT_STATE_NORMAL;
    }

    /**
     * To determine whether it is in the selection mode.
     *
     * @return true if is in the selection mode.
     */
    public boolean isSelectActivated() {
        return (mSelectState != SELECT_STATE_NORMAL);
    }

    /**
     * Sets the activation edge type, one of:
     * <ul>
     * <li>{@link EdgeType#INSIDE} for edges that respond to touches inside
     * the bounds of the host view. If touch moves outside the bounds, scrolling
     * will stop.
     * <li>{@link EdgeType#INSIDE_EXTEND} for inside edges that continued to
     * scroll when touch moves outside the bounds of the host view.
     * </ul>
     *
     * @param type The type of edge to use.
     * @return The scroll helper, which may used to chain setter calls.
     * @see EdgeType
     */
    public DragMultiSelectHelper setEdgeType(EdgeType type) {
        mEdgeType = type;
        return this;
    }

    /**
     * Sets the activation edge size relative to the host view's dimensions.
     * <p>
     * If both relative and maximum edges are specified, the maximum edge will
     * be used to constrain the calculated relative edge size.
     *
     * @param ratio The edge size as a fraction of the host view
     *              width, or {@link #RELATIVE_UNSPECIFIED} to always use the
     *              maximum value.
     * @return The scroll helper, which may used to chain setter calls.
     */
    public DragMultiSelectHelper setRelativeHotspotEdges(float ratio) {
        mHotspotRelativeEdges = ratio;
        return this;
    }

    /**
     * Sets the absolute maximum edge size.
     * <p>
     * If relative edge size is not specified, activation edges will always be
     * the maximum edge size. If both relative and maximum edges are specified,
     * the maximum edge will be used to constrain the calculated relative edge
     * size.
     *
     * @param maximumHotspotEdges The maximum edge size in pixels, or
     *                            {@link #NO_MAX} to use the unconstrained calculated relative
     *                            value.
     * @return The scroll helper, which may used to chain setter calls.
     */
    public DragMultiSelectHelper setMaximumHotspotEdges(float maximumHotspotEdges) {
        mHotspotMaximumEdges = maximumHotspotEdges;
        return this;
    }

    /**
     * Sets the target scrolling velocity relative to the host view's
     * dimensions.
     * <p>
     * If both relative and maximum velocities are specified, the maximum
     * velocity will be used to clamp the calculated relative velocity.
     *
     * @param velocity The target velocity as a fraction of the
     *                 host view width or height per second, or {@link #RELATIVE_UNSPECIFIED}
     *                 to ignore.
     * @return The scroll helper, which may used to chain setter calls.
     */
    public DragMultiSelectHelper setRelativeVelocity(float velocity) {
        mRelativeVelocity = velocity / 1000f;
        return this;
    }

    /**
     * Sets the absolute minimum scrolling velocity.
     * <p>
     * If both relative and minimum velocities are specified, the minimum
     * velocity will be used to clamp the calculated relative velocity.
     *
     * @param velocity The minimum scrolling velocity, or
     *                 {@link #NO_MIN} to leave the relative value unconstrained.
     * @return The scroll helper, which may used to chain setter calls.
     */
    public DragMultiSelectHelper setMinimumVelocity(float velocity) {
        mMinimumVelocity = velocity / 1000f;
        return this;
    }

    /**
     * Sets the absolute maximum scrolling velocity.
     * <p>
     * If relative velocity is not specified, scrolling will always reach the
     * same maximum velocity. If both relative and maximum velocities are
     * specified, the maximum velocity will be used to clamp the calculated
     * relative velocity.
     *
     * @param velocity The maximum scrolling velocity, or
     *                 {@link #NO_MAX} to leave the relative value unconstrained.
     * @return The scroll helper, which may used to chain setter calls.
     */
    public DragMultiSelectHelper setMaximumVelocity(float velocity) {
        mMaximumVelocity = velocity / 1000f;
        return this;
    }

    /**
     * Sets whether should auto enter slide mode after drag select finished.
     * It's usefully for LinearLayout RecyclerView.
     *
     * @param autoEnterSlideState should auto enter slide mode
     * @return The select helper, which may used to chain setter calls.
     */
    public DragMultiSelectHelper setAutoEnterSlideState(boolean autoEnterSlideState) {
        mShouldAutoChangeState = autoEnterSlideState;
        return this;
    }

    /**
     * Sets sliding area's start and end.
     *
     * @param start The start of the sliding area.
     * @param end   The end of the sliding area.
     * @return The select helper, which may used to chain setter calls.
     */
    public DragMultiSelectHelper setSlideArea(int start, int end) {
        mSlideAreaStart = start;
        mSlideAreaEnd = end;
        return this;
    }

    /**
     * Sets whether can drag selection in slide select mode.
     * It's usefully for LinearLayout RecyclerView.
     *
     * @param allowDragInSlideState allow drag selection in slide select mode
     * @return The select helper, which may used to chain setter calls.
     */
    public DragMultiSelectHelper setAllowDragInSlideState(boolean allowDragInSlideState) {
        mIsAllowDragInSlideState = allowDragInSlideState;
        return this;
    }

    private void activeSelectInternal(int position) {
        if (mRecyclerView == null) {
            throw new RuntimeException("Need to attach RecyclerView first");
        }

        RecyclerView.LayoutManager layoutManager = mRecyclerView.getLayoutManager();
        if (layoutManager instanceof LinearLayoutManager) {
            mDirection = ((LinearLayoutManager) layoutManager).getOrientation();
        } else {
            throw new RuntimeException("We only support LinearLayoutManager & GridLayoutManager");
        }

        if (position == RecyclerView.NO_POSITION) {
            Logger.logSelectStateChange(mSelectState, SELECT_STATE_SLIDE);
            mSelectState = SELECT_STATE_SLIDE;
        } else {
            if (!mHaveCalledSelectStart) {
                mCallback.onSelectStart(position);
                mHaveCalledSelectStart = true;
            }
            if (mSelectState == SELECT_STATE_SLIDE) {
                if (mIsAllowDragInSlideState && selectFirstItem(position)) {
                    Logger.logSelectStateChange(mSelectState, SELECT_STATE_DRAG_FROM_SLIDE);
                    mSelectState = SELECT_STATE_DRAG_FROM_SLIDE;
                }
            } else if (mSelectState == SELECT_STATE_NORMAL) {
                if (selectFirstItem(position)) {
                    Logger.logSelectStateChange(mSelectState, SELECT_STATE_DRAG_FROM_NORMAL);
                    mSelectState = SELECT_STATE_DRAG_FROM_NORMAL;
                }
            } else {
                Logger.e("activeSelect in unexpected state: " + mSelectState);
            }
        }
    }

    private boolean selectFirstItem(int position) {
        boolean selectFirstItemSucceed = mCallback.onSelectChange(position, true);
        // The drag select feature is only available if the first item is available for selection
        if (selectFirstItemSucceed) {
            mSelectionRecorder.selectFirst(position);
        }
        return selectFirstItemSucceed;
    }

    private void selectFinished(int lastItem) {
        if (lastItem != RecyclerView.NO_POSITION) {
            mCallback.onSelectEnd(lastItem);
        }
        mSelectionRecorder.clearSelect();

        mHaveCalledSelectStart = false;
        mLastTouchPosition[HORIZONTAL] = Float.MIN_VALUE;
        mLastTouchPosition[VERTICAL] = Float.MIN_VALUE;
        mScroller.setVelocity(0);
        switch (mSelectState) {
            case SELECT_STATE_DRAG_FROM_NORMAL:
                if (mShouldAutoChangeState) {
                    Logger.logSelectStateChange(mSelectState, SELECT_STATE_SLIDE);
                    mSelectState = SELECT_STATE_SLIDE;
                } else {
                    Logger.logSelectStateChange(mSelectState, SELECT_STATE_NORMAL);
                    mSelectState = SELECT_STATE_NORMAL;
                }
                break;
            case SELECT_STATE_DRAG_FROM_SLIDE:
                Logger.logSelectStateChange(mSelectState, SELECT_STATE_SLIDE);
                mSelectState = SELECT_STATE_SLIDE;
                break;
            default:
                // doesn't change the selection state
                break;
        }
    }

    private void computeTargetVelocity(int direction, float coordinate, float size) {
        final float value = getEdgeValue(mHotspotRelativeEdges, size, mHotspotMaximumEdges, coordinate);
        if (Float.compare(value, -1f) == 0) {
            mLastTouchPosition[direction] = 0;
        } else if (Float.compare(value, 1f) == 0) {
            mLastTouchPosition[direction] = size;
        } else {
            mLastTouchPosition[direction] = coordinate;
        }
        if (value == 0) {
            // The edge in this direction is not activated.
            mScroller.setVelocity(0);
        } else {
            final float targetVelocity = mRelativeVelocity * size;
            float velocity;
            if (value > 0) {
                velocity = constrain(value * targetVelocity, mMinimumVelocity, mMaximumVelocity);
            } else {
                velocity = -constrain(-value * targetVelocity, mMinimumVelocity, mMaximumVelocity);
            }
            mScroller.setVelocity(velocity);
        }

    }

    private float getEdgeValue(float relativeValue, float size, float maxValue, float current) {
        // For now, leading and trailing edges are always the same size.
        final float edgeSize = constrain(relativeValue * size, 0, maxValue);
        final float valueLeading = constrainEdgeValue(current, edgeSize);
        final float valueTrailing = constrainEdgeValue(size - current, edgeSize);
        final float value = (valueTrailing - valueLeading);
        final float interpolated;
        if (value < 0) {
            interpolated = value;
        } else if (value > 0) {
            interpolated = value;
        } else {
            return 0;
        }

        return constrain(interpolated, -1, 1);
    }

    private float constrainEdgeValue(float current, float leading) {
        if (leading == 0) {
            return 0;
        }
        if (current < leading) {
            if (current >= 0) {
                // Movement up to the edge is scaled.
                return 1f - current / leading;
            } else if (mScroller.isScrolling() && (mEdgeType == EdgeType.INSIDE_EXTEND)) {
                // Movement beyond the edge is always maximum.
                return 1f;
            }
        }
        return 0;
    }

    private float constrain(float value, float min, float max) {
        if (value > max) {
            return max;
        } else return Math.max(value, min);
    }

    private void scrollBy(int delta) {
        if (mRecyclerView == null) {
            Logger.i("scrollBy：Host view has been cleared.");
            return;
        }
        if (mDirection == VERTICAL) {
            mRecyclerView.scrollBy(0, delta);
        } else if (mDirection == HORIZONTAL) {
            mRecyclerView.scrollBy(delta, 0);
        } else {
            Logger.e("scrollBy: unknown direction =" + mDirection);
        }
        Logger.d("scrollBy: " + mLastTouchPosition[HORIZONTAL] + " " + mLastTouchPosition[VERTICAL]);
        if (mLastTouchPosition[HORIZONTAL] != Float.MIN_VALUE
                || mLastTouchPosition[VERTICAL] != Float.MIN_VALUE) {
            updateSelectedRange(mRecyclerView, mLastTouchPosition[HORIZONTAL], mLastTouchPosition[VERTICAL]);
        }
    }

    private void updateSelectedRange(@NonNull RecyclerView rv, float x, float y) {
        int position = getItemPosition(rv, x, y);
        if (position != RecyclerView.NO_POSITION && mSelectionRecorder.selectUpdate(position)) {
            for (int updateToSelectIndex : mSelectionRecorder.getUpdateToSelectIndex()) {
                mCallback.onSelectChange(updateToSelectIndex, true);
            }
            for (int updateToUnselectIndex : mSelectionRecorder.getUpdateToUnselectIndex()) {
                mCallback.onSelectChange(updateToUnselectIndex, false);
            }
        }
    }

    private int getItemPosition(@NonNull RecyclerView rv, float x, float y) {
        final View v = rv.findChildViewUnder(x, y);
        if (v == null) {
            return RecyclerView.NO_POSITION;
        }
        return rv.getChildAdapterPosition(v);
    }

    private boolean isInSlideArea(MotionEvent e) {
        float location;
        if (mDirection == VERTICAL) {
            location = e.getX();
        } else {
            location = e.getY();
        }
        return (location > mSlideAreaStart && location < mSlideAreaEnd);
    }

    /**
     * Edge type that specifies an activation area.
     *
     * @see #setEdgeType
     */
    public enum EdgeType {
        /**
         * Edge type that specifies an activation area starting at the view bounds
         * and extending inward. Moving outside the view bounds will stop scrolling.
         */
        INSIDE,

        /**
         * Edge type that specifies an activation area starting at the view bounds
         * and extending inward. After activation begins, moving outside the view
         * bounds will continue scrolling.
         */
        INSIDE_EXTEND
    }

    /**
     * This class is the contract between DragSelectTouchHelper and your application. It lets you
     * update adapter when selection start/end and state changed.
     */
    public abstract static class Callback {
        /**
         * Called when changing item state.
         *
         * @param position   this item want to change the state to new state.
         * @param isSelected true if the position should be selected, false otherwise.
         * @return Whether to set the new state successfully.
         */
        public abstract boolean onSelectChange(int position, boolean isSelected);

        /**
         * Called when selection start.
         *
         * @param start the first selected item.
         */
        public void onSelectStart(int start) { }

        /**
         * Called when selection end.
         *
         * @param end the last selected item.
         */
        public void onSelectEnd(int end) { }
    }

    /**
     * An advance Callback which provide 4 useful selection modes {@link Behavior}.
     * <p>
     * Note: Since the state of item may be repeatedly set, in order to improve efficiency,
     * please process it in the Adapter
     */
    public abstract static class AdvanceCallback<T> extends Callback {
        private Behavior mBehavior;
        private Set<T> mOriginalSelection;
        private boolean mFirstWasSelected;

        /**
         * Creates a SimpleCallback with default {@link Behavior#SelectAndReverse}# mode.
         *
         * @see Behavior
         */
        public AdvanceCallback() {
            this(Behavior.SelectAndReverse);
        }

        /**
         * Creates a SimpleCallback with select mode.
         *
         * @param behavior the initial select mode
         * @see Behavior
         */
        public AdvanceCallback(Behavior behavior) {
            setBehavior(behavior);
        }

        /**
         * Sets the select mode.
         *
         * @param behavior The type of select mode.
         * @see Behavior
         */
        public void setBehavior(Behavior behavior) {
            mBehavior = behavior;
        }

        @CallSuper
        @Override
        public void onSelectStart(int start) {
            mOriginalSelection = new HashSet<>();
            Set<T> selected = currentSelectedId();
            if (selected != null) {
                mOriginalSelection.addAll(selected);
            }
            mFirstWasSelected = mOriginalSelection.contains(getItemId(start));
        }

        @CallSuper
        @Override
        public void onSelectEnd(int end) {
            mOriginalSelection = null;
        }

        @Override
        public final boolean onSelectChange(int position, boolean isSelected) {
            boolean stateChanged;
            switch (mBehavior) {
                case SelectAndKeep: {
                    stateChanged = updateSelectState(position, true);
                    break;
                }
                case SelectAndReverse: {
                    stateChanged = updateSelectState(position, isSelected);
                    break;
                }
                case SelectAndUndo: {
                    if (isSelected) {
                        stateChanged = updateSelectState(position, true);
                    } else {
                        stateChanged = updateSelectState(position, mOriginalSelection.contains(getItemId(position)));
                    }
                    break;
                }
                case ToggleAndKeep: {
                    stateChanged = updateSelectState(position, !mFirstWasSelected);
                    break;
                }
                case ToggleAndReverse: {
                    if (isSelected) {
                        stateChanged = updateSelectState(position, !mFirstWasSelected);
                    } else {
                        stateChanged = updateSelectState(position, mFirstWasSelected);
                    }
                    break;
                }
                case ToggleAndUndo: {
                    if (isSelected) {
                        stateChanged = updateSelectState(position, !mFirstWasSelected);
                    } else {
                        stateChanged = updateSelectState(position, mOriginalSelection.contains(getItemId(position)));
                    }
                    break;
                }
                default:
                    // SelectAndReverse Mode
                    stateChanged = updateSelectState(position, isSelected);
            }
            return stateChanged;
        }

        /**
         * Get the currently selected items when selecting first item.
         *
         * @return the currently selected item's id set.
         */
        public abstract Set<T> currentSelectedId();

        /**
         * Get the ID of the item.
         *
         * @param position item position to be judged.
         * @return item's identity.
         */
        public abstract T getItemId(int position);

        /**
         * Update the selection status of the position.
         *
         * @param position   the position who's selection state changed.
         * @param isSelected true if the position should be selected, false otherwise.
         * @return Whether to set the state successfully.
         */
        public abstract boolean updateSelectState(int position, boolean isSelected);

        /**
         * Different existing selection modes
         */
        public enum Behavior {
            /**
             * Selects the first item and applies the same state to each item you go by
             * and keep the state on move back
             */
            SelectAndKeep,
            /**
             * Selects the first item and applies the same state to each item you go by
             * and applies inverted state on move back
             */
            SelectAndReverse,
            /**
             * Selects the first item and applies the same state to each item you go by
             * and reverts to the original state on move back
             */
            SelectAndUndo,
            /**
             * Toggles the first item and applies the same state to each item you go by
             * and keep the state on move back
             */
            ToggleAndKeep,
            /**
             * Toggles the first item and applies the same state to each item you go by
             * and applies inverted state on move back
             */
            ToggleAndReverse,
            /**
             * Toggles the first item and applies the same state to each item you go by
             * and reverts to the original state on move back
             */
            ToggleAndUndo,
        }
    }

    private static class AutoScroller {
        private float mVelocity = 0;
        private long mLastTime = 0;
        private int mDelta = 0;

        /**
         * Indicates automatically scroll.
         */
        private boolean mIsScrolling;

        interface ScrollStateChangeListener {
            void onScrollStateChange(boolean scroll);
        }
        private final ScrollStateChangeListener mScrollStateChangeListener;

        public AutoScroller(ScrollStateChangeListener scrollStateChangeListener) {
            mScrollStateChangeListener = scrollStateChangeListener;
        }

        public void setVelocity(float velocity) {
            Logger.d("AutoScroller setVelocity " + mVelocity + " -> " + velocity);
            if (velocity != 0) {
                boolean shouldStart = Math.abs(mVelocity) > 0
                        && Math.abs(velocity) > Math.abs(mVelocity) ;
                if (!mIsScrolling && shouldStart) {
                    mScrollStateChangeListener.onScrollStateChange(true);
                    mLastTime = SystemClock.uptimeMillis();
                    mDelta = 0;
                    mIsScrolling = true;
                }
            } else {
                mScrollStateChangeListener.onScrollStateChange(false);
                mLastTime = 0;
                mDelta = 0;
                mIsScrolling = false;
            }
            mVelocity = velocity;
        }

        public int getDelta() {
            if (mLastTime == -1) {
                Logger.e("Cannot compute scroll delta before calling start()");
                return 0;
            }
            final long currentTime = SystemClock.uptimeMillis();
            final long elapsedSinceDelta = currentTime - mLastTime;
            mLastTime = currentTime;
            mDelta = (int) (elapsedSinceDelta * mVelocity);
            Logger.d("AutoScroller spend time:" + elapsedSinceDelta);
            Logger.d("AutoScroller delta:" + mDelta);
            return mDelta;
        }

        public boolean isScrolling() {
            return mIsScrolling;
        }
    }

    private static class SelectionRecorder {
        private final List<Integer> mUpdateToSelectSet = new ArrayList<>();
        private final List<Integer> mUpdateToUnselectSet = new ArrayList<>();
        /**
         * The selected items position.
         */
        private int mStart = RecyclerView.NO_POSITION;
        private int mEnd = RecyclerView.NO_POSITION;
        private int mLastRealStart = RecyclerView.NO_POSITION;
        private int mLastRealEnd = RecyclerView.NO_POSITION;

        private void selectFirst(int position) {
            mStart = position;
            mEnd = position;
            mLastRealStart = position;
            mLastRealEnd = position;
        }

        private void clearSelect() {
            mStart = RecyclerView.NO_POSITION;
            mEnd = RecyclerView.NO_POSITION;
            mLastRealStart = RecyclerView.NO_POSITION;
            mLastRealEnd = RecyclerView.NO_POSITION;
            mUpdateToSelectSet.clear();
            mUpdateToUnselectSet.clear();
        }

        private int startPosition() {
            return mStart;
        }

        private int endPosition() {
            return mEnd;
        }

        private boolean selectUpdate(int position) {
            if (mEnd == position) {
                return false;
            }
            mEnd = position;
            int newStart, newEnd;
            newStart = Math.min(mStart, mEnd);
            newEnd = Math.max(mStart, mEnd);

            if (newStart > mLastRealStart) {
                for (int i = mLastRealStart; i <= newStart - 1; i++) {
                    mUpdateToUnselectSet.add(i);
                }
            } else if (newStart < mLastRealStart) {
                for (int i = newStart; i <= mLastRealStart - 1; i++) {
                    mUpdateToSelectSet.add(i);
                }
            }

            if (newEnd > mLastRealEnd) {
                for (int i = mLastRealEnd + 1; i <= newEnd; i++) {
                    mUpdateToSelectSet.add(i);
                }
            } else if (newEnd < mLastRealEnd) {
                for (int i = newEnd + 1; i <= mLastRealEnd; i++) {
                    mUpdateToUnselectSet.add(i);
                }
            }

            mLastRealStart = newStart;
            mLastRealEnd = newEnd;
            return true;
        }

        private int[] getUpdateToSelectIndex() {
            if (mStart == RecyclerView.NO_POSITION || mEnd == RecyclerView.NO_POSITION) {
                return new int[0];
            }
            if (mUpdateToSelectSet.size() == 0) {
                return new int[0];
            }
            Logger.i("getUpdateToSelectIndex: " + mUpdateToSelectSet.toString());
            int[] result = new int[mUpdateToSelectSet.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = mUpdateToSelectSet.get(i);
            }
            mUpdateToSelectSet.clear();
            return result;
        }

        private int[] getUpdateToUnselectIndex() {
            if (mStart == RecyclerView.NO_POSITION || mEnd == RecyclerView.NO_POSITION) {
                return new int[0];
            }
            if (mUpdateToUnselectSet.size() == 0) {
                return new int[0];
            }
            Logger.i("getUpdateToUnselectIndex: " + mUpdateToUnselectSet.toString());
            int[] result = new int[mUpdateToUnselectSet.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = mUpdateToUnselectSet.get(i);
            }
            mUpdateToUnselectSet.clear();
            return result;
        }
    }

    private static class Logger {
        private static final String TAG = "DMSH";
        private static void d(String msg) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, msg);
            }
        }

        private static void e(String msg) {
            Log.e(TAG, msg);
        }

        private static void i(String msg) {
            Log.i(TAG, msg);
        }

        private static void logSelectStateChange(int before, int after) {
            i("Select state changed: " + stateName(before) + " --> " + stateName(after));
        }

        private static String stateName(int state) {
            switch (state) {
                case SELECT_STATE_NORMAL:
                    return "NormalState";
                case SELECT_STATE_SLIDE:
                    return "SlideState";
                case SELECT_STATE_DRAG_FROM_NORMAL:
                    return "DragFromNormal";
                case SELECT_STATE_DRAG_FROM_SLIDE:
                    return "DragFromSlide";
                default:
                    return "Unknown";
            }
        }
    }
}