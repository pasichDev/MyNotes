package com.pasich.mynotes.utils.adapters.baseGenericAdapter;

import androidx.databinding.ViewDataBinding;

public interface GenericAdapterCallback<VM extends ViewDataBinding, T> {
    void bindData(VM binder, T model);
}