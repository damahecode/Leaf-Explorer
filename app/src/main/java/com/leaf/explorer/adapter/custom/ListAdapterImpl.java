package com.leaf.explorer.adapter.custom;

import android.content.Context;
import android.view.LayoutInflater;

import java.util.List;

public interface ListAdapterImpl<T>
{
    void onDataSetChanged();

    void onUpdate(List<T> passedItem);

    Context getContext();

    int getCount();

    LayoutInflater getInflater();

    List<T> getList();
}
