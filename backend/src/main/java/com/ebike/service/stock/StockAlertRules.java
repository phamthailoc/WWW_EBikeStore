package com.ebike.service.stock;

/** The pure rules of V22, kept apart from the database so they can be tested alone. */
public final class StockAlertRules {

    private StockAlertRules() {
    }

    /** Customers are told only when a colour goes from none left to some left; a top-up of a colour already in stock tells nobody. */
    public static boolean isRestock(Integer before, Integer after) {
        int old = before == null ? 0 : before;
        int now = after == null ? 0 : after;
        return old <= 0 && now > 0;
    }

    /** Waiting customers are told only about a colour and a bike that are on sale and have stock right now. */
    public static boolean canNotify(boolean variantActive, boolean productActive, Integer stock) {
        return variantActive && productActive && stock != null && stock > 0;
    }

    /** Only a working account is told: erased, suspended or otherwise disabled accounts cannot sign in to follow the link. */
    public static boolean canReceive(String accountStatus, Boolean active) {
        return "ACTIVE".equals(accountStatus) && !Boolean.FALSE.equals(active);
    }

    /** A customer can only wait for a colour that is sold out right now. */
    public static boolean canSubscribe(Integer currentStock) {
        return currentStock == null || currentStock <= 0;
    }
}
