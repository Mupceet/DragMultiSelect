package com.mupceet.dragmultiselect.demo;

import android.content.Context;
import android.graphics.Color;

import androidx.appcompat.widget.AppCompatRadioButton;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Created by flisar on 03.03.2017.
 */

public class TestAutoDataAdapter extends RecyclerView.Adapter<TestAutoDataAdapter.ViewHolder> {

    private Context mContext;
    private ItemClickListener mClickListener;
    private boolean mIsSelectMode = false;

    private List<Data> mDataList = new ArrayList<>();
    private Set<String> mSelectedIdSet = new HashSet<>();

    public TestAutoDataAdapter(Context context, int size) {
        mContext = context;
        for (int i = 0; i < size; i++) {
            mDataList.add(new Data("" + i));
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.test_cell, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Data data = mDataList.get(position);
        holder.mTextView.setText(data.mContent);

        if (mIsSelectMode) {
            holder.mRadioButton.setVisibility(View.VISIBLE);
        } else {
            holder.mRadioButton.setVisibility(View.GONE);
        }
        holder.mRadioButton.setChecked(data.isSelected);
        if (data.isSelected) {
            holder.itemView.setBackgroundColor(Color.GRAY);
        } else {
            holder.itemView.setBackgroundColor(Color.WHITE);
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
        notifyItemChanged(pos);
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
        public AppCompatRadioButton mRadioButton;

        public ViewHolder(View itemView) {
            super(itemView);
            mRadioButton = itemView.findViewById(R.id.radioButton);
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
}
