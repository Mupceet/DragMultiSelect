package com.mupceet.dragmultiselect.demo;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.mupceet.dragmultiselect.DragSelectTouchHelper;
import com.mupceet.dragmultiselect.DragSelectTouchHelper.AdvanceCallback;

import java.util.Set;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";
    private DragSelectTouchHelper mDragSelectTouchHelper;
    private Toolbar mToolbar;
    private RecyclerView rvData;
    private GridLayoutManager glm;
    private LinearLayoutManager llm;
    private TestAutoDataAdapter mAdapter;
    private AdvanceCallback mDragSelectTouchHelperCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mToolbar = findViewById(R.id.toolbar);
        mToolbar.setTitle("DragSelectRecyclerView");
        setSupportActionBar(mToolbar);

        // Prepare the RecyclerView (init LayoutManager and set Adapter)
        rvData = findViewById(R.id.rvData);
        updateLayoutManager();
        mAdapter = new TestAutoDataAdapter(this, 500);
        rvData.setAdapter(mAdapter);
        ((SimpleItemAnimator) rvData.getItemAnimator()).setSupportsChangeAnimations(false);
        mAdapter.setClickListener(new TestAutoDataAdapter.ItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (mAdapter.isSelectMode()) {
                    mAdapter.toggleSelection(position);
                }
            }

            @Override
            public boolean onItemLongClick(View view, int position) {
                Log.d(TAG, "onItemLongClick: ");
                // if one item is long pressed, we start the drag selection like following:
                // we just call this function and pass in the position of the first selected item
                // the selection processor does take care to update the positions selection mode correctly
                // and will correctly transform the touch events so that they can be directly applied to your adapter!!!
                // 4. 适当的时候进入选择模式
                mAdapter.setSelectMode(true);
                mDragSelectTouchHelper.activeDragSelect(position);
                return true;
            }
        });
        // 1. 创建 Callback
        mDragSelectTouchHelperCallback = new AdvanceCallback<String>(AdvanceCallback.Mode.SelectAndReverse) {
            @Override
            public Set<String> currentSelectedId() {
                return mAdapter.getSelectionSet();
            }

            @Override
            public String getItemId(int position) {
                return mAdapter.getItemInfo(position);
            }

            @Override
            public boolean updateSelectState(int position, boolean isSelected) {
                Log.d(TAG, "updateSelectState: " + position);
                // 更新该条目的状态
                return mAdapter.select(position, isSelected);
            }

            @Override
            public void onSelectStart(int start) {
                super.onSelectStart(start);
                Log.i(TAG, "onSelectStart: " + start);
            }

            @Override
            public void onSelectEnd(int end) {
                super.onSelectEnd(end);
                Log.i(TAG, "onSelectEnd: " + end);
            }
        };
        // 2. 创建 SelectTouchHelper
        mDragSelectTouchHelper = new DragSelectTouchHelper(mDragSelectTouchHelperCallback)
                .setSlideArea(0, 64)
                .setAllowDragInSlideState(true);
        // 3. 将 Helper 与 RecyclerView 关联
        mDragSelectTouchHelper.attachToRecyclerView(rvData);
        mToolbar.setSubtitle("Mode: " + AdvanceCallback.Mode.SelectAndReverse.name());
    }

    private void updateLayoutManager() {
        if (glm == null || llm == null) {
            glm = new GridLayoutManager(this, 3, GridLayoutManager.VERTICAL, false);
            llm = new LinearLayoutManager(this);
        }
        RecyclerView.LayoutManager layoutManager = rvData.getLayoutManager();
        if (layoutManager == null) {
            rvData.setLayoutManager(llm);
        }

        if (layoutManager instanceof GridLayoutManager) {
            rvData.setLayoutManager(llm);
        } else if (layoutManager instanceof LinearLayoutManager) {
            rvData.setLayoutManager(glm);
        }
    }

    // ---------------------
    // Selection Listener
    // ---------------------

    private void updateSelectionListener(AdvanceCallback.Mode mode) {
        mDragSelectTouchHelperCallback.setMode(mode);
        mToolbar.setSubtitle("Mode: " + mode.name());
    }

    // ---------------------
    // Menu
    // ---------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_clear) {
            mAdapter.deselectAll();
        } else if (item.getItemId() == R.id.menu_select_all) {
            mAdapter.selectAll();
        } else if (item.getItemId() == R.id.menu_change_layout_manager) {
            updateLayoutManager();
        } else if (item.getItemId() == R.id.menu_active_slide) {
            mDragSelectTouchHelper.activeSlideSelect();
            mAdapter.setSelectMode(true);
        } else if (item.getItemId() == R.id.mode_select_and_keep) {
            updateSelectionListener(AdvanceCallback.Mode.SelectAndKeep);
        } else if (item.getItemId() == R.id.mode_select_and_reverse) {
            updateSelectionListener(AdvanceCallback.Mode.SelectAndReverse);
        } else if (item.getItemId() == R.id.mode_select_and_undo) {
            updateSelectionListener(AdvanceCallback.Mode.SelectAndUndo);
        } else if (item.getItemId() == R.id.mode_toggle_and_keep) {
            updateSelectionListener(AdvanceCallback.Mode.ToggleAndKeep);
        } else if (item.getItemId() == R.id.mode_toggle_and_reverse) {
            updateSelectionListener(AdvanceCallback.Mode.ToggleAndReverse);
        } else if (item.getItemId() == R.id.mode_toggle_and_undo) {
            updateSelectionListener(AdvanceCallback.Mode.ToggleAndUndo);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (mAdapter.isSelectMode()) {
            mAdapter.setSelectMode(false);
            mDragSelectTouchHelper.inactiveSelect();
        } else {
            super.onBackPressed();
        }
    }
}