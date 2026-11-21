package com.ebike.service.testride;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** V23: every half hour, reminds customers of tomorrow's rides and closes requests that nobody confirmed in time. */
@Component
public class TestRideJob {

    private static final Logger log = LoggerFactory.getLogger(TestRideJob.class);

    private final TestRideReminderService service;

    public TestRideJob(TestRideReminderService service) {
        this.service = service;
    }

    @Scheduled(cron = "${app.test-ride.cron:0 */30 * * * *}")
    public void run() {
        LocalDateTime now = LocalDateTime.now();
        // the two tasks are independent: a failure of one must not stop the other, nor the next run
        try {
            int closed = service.expireUnconfirmed(now);
            if (closed > 0) {
                log.info("Closed {} unconfirmed test ride request(s)", closed);
            }
        } catch (RuntimeException exception) {
            log.warn("Closing unconfirmed test rides failed: {}", exception.getMessage());
        }
        try {
            int sent = service.sendReminders(now);
            if (sent > 0) {
                log.info("Sent {} test ride reminder(s)", sent);
            }
        } catch (RuntimeException exception) {
            log.warn("Test ride reminder run failed: {}", exception.getMessage());
        }
    }
}
