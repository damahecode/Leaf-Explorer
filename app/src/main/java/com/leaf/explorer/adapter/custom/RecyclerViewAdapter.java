package com.leaf.explorer.adapter.custom;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

abstract public class RecyclerViewAdapter<T, V extends RecyclerViewAdapter.ViewHolder>
        extends RecyclerView.Adapter<V>
        implements ListAdapterImpl<T>
{
    private Context mContext;
    private LayoutInflater mInflater;
    private boolean mHorizontalOrientation;

    public RecyclerViewAdapter(Context context)
    {
        mContext = context;
        mInflater = LayoutInflater.from(context);
    }

    @Override
    public void onDataSetChanged()
    {
        notifyDataSetChanged();
    }

    public boolean isHorizontalOrientation()
    {
        return mHorizontalOrientation;
    }

    public Context getContext()
    {
        return mContext;
    }

    @Override
    public int getCount()
    {
        return getItemCount();
    }

    public LayoutInflater getInflater()
    {
        return mInflater;
    }

    public void setUseHorizontalOrientation(boolean use)
    {
        mHorizontalOrientation = use;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
    {
        private View mView;

        public ViewHolder(View itemView)
        {
            super(itemView);
            mView = itemView;
        }

        public View getView()
        {
            return mView;
        }
    }

    public interface OnClickListener
    {
        void onClick(ViewHolder holder);
    }
}
