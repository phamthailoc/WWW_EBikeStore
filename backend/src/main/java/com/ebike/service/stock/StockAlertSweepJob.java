package com.ebike.service.stock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * V22: catches the cases the stock update itself cannot see: a colour put back on sale (by a script or a future
 * admin screen) while it already has stock, and a customer whose request was saved just as a restock committed (the
 * request also checks the stock again after saving, so this is only the safety net). Customers still waiting for an
 * in-stock, on-sale colour are told. The first run is delayed after start-up and later runs follow the configured interval.
 */
@Component
public class StockAlertSweepJob {

    private static final Logger log = LoggerFactory.getLogger(StockAlertSweepJob.class);

    private final StockAlertService stockAlerts;

    public StockAlertSweepJob(StockAlertService stockAlerts) {
        this.stockAlerts = stockAlerts;
    }

    @Scheduled(fixedDelayString = "${app.stock-alert.sweep-interval:PT5M}", initialDelayString = "${app.stock-alert.sweep-initial-delay:PT2M}")
    public void sweep() {
        int told = 0;
        try {
            for (Long variantId : stockAlerts.waitingVariantIdsInStock()) {
                try {
                    told += stockAlerts.notifyRestockedById(variantId);
                } catch (RuntimeException exception) {
                    log.warn("Back-in-stock sweep failed for colour {}: {}", variantId, exception.getMessage());
                }
            }
        } catch (RuntimeException exception) {
            log.warn("Back-in-stock sweep failed: {}", exception.getMessage());
        }
        if (told > 0) {
            log.info("Back-in-stock sweep told {} waiting customer(s)", told);
        }
    }
}
