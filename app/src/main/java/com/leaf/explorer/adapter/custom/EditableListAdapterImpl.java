package com.leaf.explorer.adapter.custom;

import com.genonbeta.android.framework.util.NotReadyException;

public interface EditableListAdapterImpl<T> extends ListAdapterImpl<T>
{
    T getItem(int position) throws NotReadyException;
}
