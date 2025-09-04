package com.pasich.mynotes.di;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.cache.ExternalPreferredCacheDiskCacheFactory;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.GlideBuilder;

@GlideModule
public final class MyAppGlideModule extends AppGlideModule {

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        // Динамічний розрахунок розміру кешу на основі пам'яті пристрою
        MemorySizeCalculator calculator = new MemorySizeCalculator.Builder(context)
                .setMemoryCacheScreens(2) // кеш для 2 екранів
                .build();

        builder.setMemoryCache(new LruResourceCache(calculator.getMemoryCacheSize()));

        int diskCacheSizeBytes = 200 * 1024 * 1024; // 200 MB
        builder.setDiskCache(new ExternalPreferredCacheDiskCacheFactory(
                context,
                "mynotes_glide_cache",
                diskCacheSizeBytes
        ));
    }

    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
}