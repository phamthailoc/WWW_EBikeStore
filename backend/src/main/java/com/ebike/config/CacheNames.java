package com.ebike.config;

/** Names of the in-memory caches. Anything that changes what a product page shows must evict both. */
public final class CacheNames {

    public static final String PRODUCT_LIST_CACHE = "products:v2:list";
    public static final String PRODUCT_DETAIL_CACHE = "products:v2:detail";

    private CacheNames() {
    }
}
