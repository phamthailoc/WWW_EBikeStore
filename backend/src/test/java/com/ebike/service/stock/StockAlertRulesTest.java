package com.ebike.service.stock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** V22: when a customer may wait for a colour and when the shop tells them it is back. */
class StockAlertRulesTest {

    @Test
    void soldOutColourGoingToInStockIsARestock() {
        assertTrue(StockAlertRules.isRestock(0, 5));
        assertTrue(StockAlertRules.isRestock(null, 1));
    }

    @Test
    void topUpOfColourAlreadyInStockTellsNobody() {
        assertFalse(StockAlertRules.isRestock(2, 10));
    }

    @Test
    void staying_or_going_to_zeroIsNotARestock() {
        assertFalse(StockAlertRules.isRestock(0, 0));
        assertFalse(StockAlertRules.isRestock(3, 0));
        assertFalse(StockAlertRules.isRestock(0, null));
    }

    @Test
    void onlySoldOutColoursCanBeWaitedFor() {
        assertTrue(StockAlertRules.canSubscribe(0));
        assertTrue(StockAlertRules.canSubscribe(null));
        assertFalse(StockAlertRules.canSubscribe(1));
    }
}
