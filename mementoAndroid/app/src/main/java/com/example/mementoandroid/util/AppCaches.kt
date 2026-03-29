package com.example.mementoandroid.util

import android.content.Context
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader

/**
 * Clears Coil memory and disk caches so remote images refetch after a pull-to-refresh.
 */
@OptIn(ExperimentalCoilApi::class)
fun clearCoilCaches(context: Context) {
    context.imageLoader.memoryCache?.clear()
    context.imageLoader.diskCache?.clear()
}
