package com.ebike.service.testride;

import java.time.LocalDateTime;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** V23: every half hour, reminds customers of tomorrow's rides and closes requests that nobody confirmed in time. */
@Component
public class TestRideJob {

    private static final Logger log = LoggerFactory.getLogger(TestRideJob.class);

    private final TestRideReminderService service;
    private final ZoneId zone;

    public TestRideJob(TestRideReminderService service, @Value("${app.time-zone:Asia/Ho_Chi_Minh}") String timeZone) {
        this.service = service;
        this.zone = ZoneId.of(timeZone);
    }

    @Scheduled(cron = "${app.test-ride.cron:0 */30 * * * *}", zone = "${app.time-zone:Asia/Ho_Chi_Minh}")
    public void run() {
        LocalDateTime now = LocalDateTime.now(zone); // the showroom's local time, like the slots
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
