package com.ebike.service.warranty;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** V20: once a day, reminds customers whose vehicle reached a maintenance milestone. */
@Component
public class MaintenanceReminderJob {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceReminderJob.class);

    private final MaintenanceReminderService service;

    public MaintenanceReminderJob(MaintenanceReminderService service) {
        this.service = service;
    }

    @Scheduled(cron = "${app.maintenance.cron:0 0 8 * * *}")
    public void sendReminders() {
        try {
            int sent = service.sendDueReminders(LocalDateTime.now());
            if (sent > 0) {
                log.info("Sent {} maintenance reminder(s)", sent);
            }
        } catch (RuntimeException exception) {
            // one bad row must not stop tomorrow's run
            log.warn("Maintenance reminder run failed: {}", exception.getMessage());
        }
    }
}
