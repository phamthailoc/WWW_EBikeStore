package com.ebike.service.testride;

import com.ebike.listener.notification.NotificationEventBridge;
import com.ebike.model.entity.testride.TestRideBooking;
import com.ebike.repository.testride.TestRideBookingRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V23: the two things the test-ride job does on a timer. It reminds customers of a confirmed ride that starts within the next
 * {@code app.test-ride.reminder-hours} (once), and it closes requests that nobody confirmed or closed by hand before their slot was over, telling the customer.
 *
 * <p>Like the maintenance reminders (V20), each booking is handled in its own transaction: one that fails is logged and left for the next
 * run, and the notice and email go out only after that booking's row was saved. A run handles at most {@code max-per-run} bookings of each kind
 * (the mail queue holds 500): the rest wait for the next run.
 */
@Service
public class TestRideReminderService {

    private static final Logger log = LoggerFactory.getLogger(TestRideReminderService.class);
    static final int DEFAULT_MAX_PER_RUN = 100;

    private final TestRideBookingRepository bookings;
    private final TransactionTemplate perBooking;
    private final int reminderHours;
    private final int maxPerRun;

    @Autowired
    public TestRideReminderService(
        TestRideBookingRepository bookings,
        PlatformTransactionManager transactionManager,
        @Value("${app.test-ride.reminder-hours:24}") int reminderHours,
        @Value("${app.test-ride.max-per-run:" + DEFAULT_MAX_PER_RUN + "}") int maxPerRun
    ) {
        this.bookings = bookings;
        this.perBooking = new TransactionTemplate(transactionManager);
        this.perBooking.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.reminderHours = Math.max(1, reminderHours);
        this.maxPerRun = Math.max(1, maxPerRun);
    }

    /** Returns how many reminders were sent. */
    public int sendReminders(LocalDateTime now) {
        List<Long> ids = bookings.findReminderDueIds(now, now.plusHours(reminderHours), PageRequest.of(0, maxPerRun));
        int sent = 0;
        for (Long id : ids) {
            try {
                Boolean done = perBooking.execute(status -> remind(id, now));
                sent += Boolean.TRUE.equals(done) ? 1 : 0;
            } catch (RuntimeException exception) {
                log.warn("Test ride reminder for booking {} failed, left for the next run: {}", id, exception.getMessage());
            }
        }
        return sent;
    }

    /** Returns how many requests were closed. */
    public int expireUnconfirmed(LocalDateTime now) {
        // a request is closed only when its whole slot is over: the staff can still mark a ride that happened or a no-show while the slot runs
        LocalDateTime cutoff = TestRideRules.expiryCutoff(now);
        List<Long> ids = bookings.findExpiredRequestIds(cutoff, PageRequest.of(0, maxPerRun));
        int closed = 0;
        for (Long id : ids) {
            try {
                Boolean done = perBooking.execute(status -> expire(id, now, cutoff));
                closed += Boolean.TRUE.equals(done) ? 1 : 0;
            } catch (RuntimeException exception) {
                log.warn("Closing test ride request {} failed, left for the next run: {}", id, exception.getMessage());
            }
        }
        return closed;
    }

    private boolean remind(Long id, LocalDateTime now) {
        // read again under the row lock: the customer may have cancelled, or another run may have sent it, since the ids were read
        TestRideBooking booking = bookings.findLockedById(id).orElse(null);
        if (booking == null || !TestRideRules.reminderDue(booking.getStatus(), booking.getSlotStart(), booking.getReminderSentAt(), now, reminderHours)
            || !TestRideService.isAvailable(booking)) {
            // a ride at a showroom that was switched off or of a bike no longer sold will not take place: no reminder
            return false;
        }
        booking.setReminderSentAt(now);
        booking.setUpdatedAt(now);
        bookings.save(booking);
        NotificationEventBridge.publish(TestRideService.noticeOf(booking, TestRideMessages.REMINDER));
        return true;
    }

    private boolean expire(Long id, LocalDateTime now, LocalDateTime cutoff) {
        TestRideBooking booking = bookings.findLockedById(id).orElse(null);
        if (booking == null || !TestRideBooking.REQUESTED.equals(booking.getStatus()) || booking.getSlotStart().isAfter(cutoff)) {
            return false;
        }
        booking.setStatus(TestRideBooking.CANCELLED);
        booking.setCancelSource(TestRideBooking.BY_EXPIRY);
        booking.setUpdatedAt(now);
        bookings.save(booking);
        NotificationEventBridge.publish(TestRideService.noticeOf(booking, TestRideMessages.EXPIRED));
        return true;
    }
}
