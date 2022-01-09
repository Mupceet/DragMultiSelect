package com.mupceet.dragmultiselect.demo;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by flisar on 03.03.2017.
 */

public class TestAutoDataAdapter extends RecyclerView.Adapter<TestAutoDataAdapter.ViewHolder> {

    private ItemClickListener mClickListener;
    private boolean mIsSelectMode = false;

    private final List<Data> mDataList = new ArrayList<>();
    private final Set<String> mSelectedIdSet = new HashSet<>();

    public TestAutoDataAdapter(int size) {
        for (int i = 0; i < size; i++) {
            mDataList.add(new Data("" + i));
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.test_cell, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.itemView.setBackground(new ColorDrawable(COLORS[position % 26]));
        Data data = mDataList.get(position);
        holder.mTextView.setText(data.mContent);
        if (data.isSelected) {
            holder.itemView.setForeground(new ColorDrawable(Color.parseColor("#B3000000")));
        } else {
            holder.itemView.setForeground(null);
        }
    }

    @Override
    public int getItemCount() {
        return mDataList.size();
    }

    // ----------------------
    // Selection
    // ----------------------
    public boolean isSelectMode() {
        return mIsSelectMode;
    }

    public void setSelectMode(boolean selectMode) {
        if (mIsSelectMode == selectMode) {
            return;
        }
        mIsSelectMode = selectMode;
        if (!mIsSelectMode) {
            deselectAll();
        }
        notifyDataSetChanged();
    }


    public boolean toggleSelection(int pos) {
        if (pos == 6) {
            return false;
        }
        Data data = mDataList.get(pos);
        data.isSelected = !data.isSelected;
        notifyItemChanged(pos, data.isSelected);
        if (data.isSelected) {
            mSelectedIdSet.add(getItemInfo(pos));
        } else {
            mSelectedIdSet.remove(getItemInfo(pos));
        }
        return true;
    }

    public boolean select(int pos, boolean selected) {
        if (pos == 6) {
            return false;
        }
        Data data = mDataList.get(pos);
        data.isSelected = selected;
        notifyItemChanged(pos);
        if (data.isSelected) {
            mSelectedIdSet.add(getItemInfo(pos));
        } else {
            mSelectedIdSet.remove(getItemInfo(pos));
        }
        notifyItemChanged(pos);
        return true;
    }

    public void deselectAll() {
        mSelectedIdSet.clear();
        for (Data data : mDataList) {
            data.isSelected = false;
        }
        notifyDataSetChanged();
    }

    public void selectAll() {
        for (int i = 0; i < mDataList.size(); i++) {
            Data data = mDataList.get(i);
            data.isSelected = true;
            mSelectedIdSet.add(getItemInfo(i));
        }
        notifyDataSetChanged();
    }

    public Set<String> getSelectionSet() {
        return mSelectedIdSet;
    }

    // ----------------------
    // Click Listener
    // ----------------------
    public void setClickListener(ItemClickListener itemClickListener) {
        mClickListener = itemClickListener;
    }

    public String getItemInfo(int position) {
        return String.valueOf(position);
    }

    public interface ItemClickListener {
        void onItemClick(View view, int position);

        boolean onItemLongClick(View view, int position);
    }

    private static class Data {
        private boolean isSelected;
        private String mContent;

        public Data(String content) {
            isSelected = false;
            mContent = content;
        }
    }

    // ----------------------
    // ViewHolder
    // ----------------------

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
        public AppCompatTextView mTextView;

        public ViewHolder(View itemView) {
            super(itemView);
            mTextView = itemView.findViewById(R.id.tvText);
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
        }

        @Override
        public void onClick(View view) {
            if (mClickListener != null) {
                mClickListener.onItemClick(view, getAdapterPosition());
            }
        }

        @Override
        public boolean onLongClick(View view) {
            if (mClickListener != null) {
                return mClickListener.onItemLongClick(view, getAdapterPosition());
            }
            return false;
        }
    }

    private static final int[] COLORS = new int[]{
            Color.parseColor("#F44336"), Color.parseColor("#E91E63"),
            Color.parseColor("#9C27B0"), Color.parseColor("#673AB7"),
            Color.parseColor("#3F51B5"), Color.parseColor("#2196F3"),
            Color.parseColor("#03A9F4"), Color.parseColor("#00BCD4"),
            Color.parseColor("#009688"), Color.parseColor("#4CAF50"),
            Color.parseColor("#8BC34A"), Color.parseColor("#CDDC39"),
            Color.parseColor("#FFEB3B"), Color.parseColor("#FFC107"),
            Color.parseColor("#FF9800"), Color.parseColor("#FF5722"),
            Color.parseColor("#795548"), Color.parseColor("#9E9E9E"),
            Color.parseColor("#607D8B"), Color.parseColor("#F44336"),
            Color.parseColor("#E91E63"), Color.parseColor("#9C27B0"),
            Color.parseColor("#673AB7"), Color.parseColor("#3F51B5"),
            Color.parseColor("#2196F3"), Color.parseColor("#03A9F4")
    };
}
