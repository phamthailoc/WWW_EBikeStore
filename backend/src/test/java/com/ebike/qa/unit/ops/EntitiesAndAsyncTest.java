package com.ebike.qa.unit.ops;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebike.config.AsyncConfiguration;
import com.ebike.listener.notification.NotificationEventBridge;
import com.ebike.model.entity.notification.Notification;
import com.ebike.model.entity.notification.NotificationType;
import com.ebike.model.entity.stock.StockMovement;
import com.ebike.model.entity.stock.StockMovementReason;
import com.ebike.model.entity.stock.StockReceipt;
import com.ebike.model.entity.stock.StockReceiptLine;
import com.ebike.model.entity.warranty.MaintenanceReminder;
import com.ebike.model.entity.warranty.WarrantyClaim;
import jakarta.persistence.Column;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

class EntitiesAndAsyncTest {

    @Test
    void newClaimStartsNewAndInWarranty() {
        WarrantyClaim c = new WarrantyClaim();
        assertThat(c.getStatus()).isEqualTo("NEW");
        assertThat(c.isOutOfWarranty()).isFalse();
    }

    @Test
    void claimTimestampsSetOnPersistAndUpdatedOnUpdate() {
        WarrantyClaim c = new WarrantyClaim();
        ReflectionTestUtils.invokeMethod(c, "onCreate");
        assertThat(c.getCreatedAt()).isNotNull();
        assertThat(c.getUpdatedAt()).isEqualTo(c.getCreatedAt());
        ReflectionTestUtils.setField(c, "updatedAt", LocalDateTime.of(2000, 1, 1, 0, 0));
        ReflectionTestUtils.invokeMethod(c, "onUpdate");
        assertThat(c.getUpdatedAt()).isAfter(LocalDateTime.of(2000, 1, 1, 0, 0));
        assertThat(c.getCreatedAt()).isNotEqualTo(LocalDateTime.of(2000, 1, 1, 0, 0));
    }

    @Test
    void claimStatusConstantsMatchRules() {
        assertThat(new String[] {WarrantyClaim.NEW, WarrantyClaim.RECEIVED, WarrantyClaim.REPAIRING, WarrantyClaim.DONE, WarrantyClaim.REJECTED})
            .containsExactly("NEW", "RECEIVED", "REPAIRING", "DONE", "REJECTED");
    }

    @Test
    void reminderIsUniquePerUnitAndMilestoneAndDefaultsToNotifiedTrue() {
        Table t = MaintenanceReminder.class.getAnnotation(Table.class);
        UniqueConstraint uc = t.uniqueConstraints()[0];
        assertThat(uc.columnNames()).containsExactlyInAnyOrder("order_item_unit_id", "milestone");
        assertThat(new MaintenanceReminder().isNotified()).isTrue();
        MaintenanceReminder r = new MaintenanceReminder();
        ReflectionTestUtils.invokeMethod(r, "onCreate");
        assertThat((LocalDateTime) ReflectionTestUtils.getField(r, "createdAt")).isNotNull();
    }

    @Test
    void receiptAddLineLinksBothSides() {
        StockReceipt receipt = new StockReceipt();
        StockReceiptLine line = new StockReceiptLine();
        receipt.addLine(line);
        assertThat(line.getReceipt()).isSameAs(receipt);
        assertThat(receipt.getLines()).containsExactly(line);
        assertThat(receipt.getCreatedAt()).isNotNull();
    }

    @Test
    void movementDefaultsAndColumnLimits() throws Exception {
        StockMovement m = new StockMovement();
        assertThat(m.getCreatedAt()).isNotNull();
        assertThat(StockMovement.class.getDeclaredField("note").getAnnotation(Column.class).length()).isEqualTo(500);
        assertThat(StockReceipt.class.getDeclaredField("supplierName").getAnnotation(Column.class).length()).isEqualTo(150);
        assertThat(StockReceipt.class.getDeclaredField("note").getAnnotation(Column.class).length()).isEqualTo(500);
        assertThat(StockReceiptLine.class.getDeclaredField("colorName").getAnnotation(Column.class).length()).isEqualTo(100);
    }

    @Test
    void movementReasonsAreTheFiveDocumentedOnes() {
        assertThat(StockMovementReason.values()).extracting(Enum::name)
            .containsExactly("RECEIPT", "SALE", "CANCEL_RESTORE", "ADJUSTMENT", "OPENING");
    }

    @Test
    void notificationTimestampAndUnreadByDefault() {
        Notification n = new Notification();
        assertThat(n.getReadAt()).isNull();
        ReflectionTestUtils.invokeMethod(n, "onCreate");
        assertThat(n.getCreatedAt()).isNotNull();
        n.setType(NotificationType.STOCK_ALERT);
        assertThat(n.getType().name()).isEqualTo("STOCK_ALERT");
    }

    @Test
    void notificationTypesUsedByTheServicesExist() {
        assertThat(NotificationType.values()).extracting(Enum::name).contains(
            "ORDER_STATUS", "PAYMENT_SUCCESS", "PAYMENT_FAILED", "STOCK_ALERT", "WARRANTY_CLAIM", "MAINTENANCE_DUE", "TEST_RIDE", "INSTALLMENT");
        // the target-url column holds 255 characters (StockAlertMessages.MAX_TARGET_URL_LENGTH)
        try {
            assertThat(Notification.class.getDeclaredField("targetUrl").getAnnotation(Column.class).length()).isEqualTo(255);
            assertThat(Notification.class.getDeclaredField("title").getAnnotation(Column.class).length()).isEqualTo(180);
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }
    }

    // ---------------- event bridge ----------------
    @Test
    void bridgeIgnoresNullEventsAndMissingPublisher() {
        new NotificationEventBridge().setApplicationEventPublisher(null);
        NotificationEventBridge.publish(new Object());
        java.util.List<Object> events = Fx.captureEvents();
        NotificationEventBridge.publish(null);
        assertThat(events).isEmpty();
        NotificationEventBridge.publish("x");
        assertThat(events).containsExactly("x");
        Fx.release();
    }

    // ---------------- async configuration ----------------
    @Test
    void mailPoolDropsOverflowInsteadOfThrowingOrBlocking() throws Exception {
        ThreadPoolTaskExecutor ex = (ThreadPoolTaskExecutor) new AsyncConfiguration().mailExecutor();
        ex.initialize();
        try {
            assertThat(ex.getCorePoolSize()).isEqualTo(2);
            assertThat(ex.getMaxPoolSize()).isEqualTo(4);
            assertThat(ex.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(500);
            CountDownLatch block = new CountDownLatch(1);
            // 4 threads + 500 queue slots are busy; the next submissions are dropped silently
            for (int i = 0; i < 504; i++) ex.execute(() -> await(block));
            ex.execute(() -> { throw new AssertionError("must have been dropped"); });
            assertThat(ex.getThreadPoolExecutor().getQueue().size()).isEqualTo(500);
            block.countDown();
        } finally {
            ex.shutdown();
        }
    }

    @Test
    void notificationPoolRunsOverflowOnCallerSoNothingIsLost() throws Exception {
        ThreadPoolTaskExecutor ex = (ThreadPoolTaskExecutor) new AsyncConfiguration().notificationExecutor();
        ex.initialize();
        try {
            assertThat(ex.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(1000);
            CountDownLatch block = new CountDownLatch(1);
            for (int i = 0; i < 1004; i++) ex.execute(() -> await(block));
            AtomicReference<Thread> ranOn = new AtomicReference<>();
            ex.execute(() -> ranOn.set(Thread.currentThread()));
            assertThat(ranOn.get()).isSameAs(Thread.currentThread());
            block.countDown();
        } finally {
            ex.shutdown();
        }
    }

    @Test
    void documentPoolIsSeparateAndRejectsWhenFull() {
        ThreadPoolTaskExecutor docs = new AsyncConfiguration().documentExecutor();
        ThreadPoolTaskExecutor mail = (ThreadPoolTaskExecutor) new AsyncConfiguration().mailExecutor();
        assertThat(docs).isNotSameAs(mail);
        assertThat(AsyncConfiguration.DOCUMENT_EXECUTOR).isNotEqualTo(AsyncConfiguration.MAIL_EXECUTOR);
        docs.initialize();
        try {
            Executor e = docs;
            CountDownLatch block = new CountDownLatch(1);
            boolean rejected = false;
            try {
                for (int i = 0; i < 5000 && !rejected; i++) e.execute(() -> await(block));
            } catch (java.util.concurrent.RejectedExecutionException ex) {
                rejected = true;
            }
            block.countDown();
            assertThat(rejected).as("a full document queue must be visible to the listener").isTrue();
        } finally {
            docs.shutdown();
        }
    }

    private static void await(CountDownLatch l) {
        try {
            l.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
