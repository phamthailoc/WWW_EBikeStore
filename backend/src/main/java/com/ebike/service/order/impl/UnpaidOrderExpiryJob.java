package com.ebike.service.order.impl;

import com.ebike.service.order.OrderService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically cancels orders that were never paid or confirmed, so their bikes go back on sale. */
@Component
public class UnpaidOrderExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(UnpaidOrderExpiryJob.class);

    private final OrderService orderService;
    private final Clock clock;

    @Autowired
    public UnpaidOrderExpiryJob(OrderService orderService) {
        this(orderService, Clock.systemDefaultZone());
    }

    UnpaidOrderExpiryJob(OrderService orderService, Clock clock) {
        this.orderService = orderService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.order.expiry-check-interval:PT5M}", initialDelayString = "PT1M")
    public void expireUnpaidOrders() {
        int cancelled = orderService.expireUnpaidOrders(LocalDateTime.now(clock));
        if (cancelled > 0) {
            log.info("Cancelled {} unpaid order(s) past their payment window", cancelled);
        }
    }
}
