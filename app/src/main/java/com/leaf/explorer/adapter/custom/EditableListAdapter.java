package com.leaf.explorer.adapter.custom;

import android.content.Context;
import android.view.View;
import com.genonbeta.android.framework.util.NotReadyException;
import java.util.ArrayList;
import java.util.List;

abstract public class EditableListAdapter<T, V extends EditableListAdapter.EditableViewHolder>
        extends RecyclerViewAdapter<T, V>
        implements EditableListAdapterImpl<T>
{

    private final List<T> mItemList = new ArrayList<>();

    public EditableListAdapter(Context context)
    {
        super(context);
        setHasStableIds(true);
    }

    @Override
    public void onUpdate(List<T> passedItem)
    {
        synchronized (getItemList()) {
            mItemList.clear();
            mItemList.addAll(passedItem);
        }
    }

    public void submitList(List<T> items) {
        onUpdate(items);
        onDataSetChanged();
    }

    @Override
    public int getCount()
    {
        return getItemList().size();
    }

    @Override
    public int getItemCount()
    {
        return getCount();
    }

    public T getItem(int position) throws NotReadyException
    {
        if (position >= getCount() || position < 0)
            throw new NotReadyException("The list does not contain  this index: " + position);

        return getList().get(position);
    }

    public T getItem(V holder) throws NotReadyException
    {
        return getItem(holder.getAbsoluteAdapterPosition());
    }

//    @Override
//    public long getItemId(int position)
//    {
//        try {
//            return getItem(position).getListId();
//        } catch (NotReadyException e) {
//            e.printStackTrace();
//        }
//
//        // This may be changed in the future
//        return Utils.getUniqueNumber();
//    }

    private List<T> getItemList()
    {
        return mItemList;
    }

    @Override
    public List<T> getList()
    {
        return getItemList();
    }

    public static class EditableViewHolder extends ViewHolder
    {
        private View mClickableView;

        public EditableViewHolder(View itemView)
        {
            super(itemView);
        }

        public View getClickableView()
        {
            return mClickableView == null ? getView() : mClickableView;
        }

        public EditableViewHolder setClickableView(int resId)
        {
            return setClickableView(getView().findViewById(resId));
        }

        public EditableViewHolder setClickableView(View clickableLayout)
        {
            mClickableView = clickableLayout;
            return this;
        }
    }

}
