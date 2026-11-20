package com.ebike.model.entity.warranty;

import com.ebike.model.entity.order.OrderItemUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/** V20: records that a maintenance milestone of a vehicle was handled, so the daily job never reminds twice. */
@Entity
@Table(
    name = "maintenance_reminders",
    uniqueConstraints = @UniqueConstraint(name = "uk_maintenance_reminders_unit_milestone", columnNames = {"order_item_unit_id", "milestone"})
)
public class MaintenanceReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_unit_id", nullable = false)
    private OrderItemUnit unit;

    /** The interval text such as P6M, so changing the order of the configured list does not shift old rows. */
    @Column(nullable = false, length = 20)
    private String milestone;

    @Column(name = "due_at", nullable = false)
    private LocalDateTime dueAt;

    /** False for an older milestone that was passed over because a later one was already due. */
    @Column(nullable = false)
    private boolean notified = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public OrderItemUnit getUnit() { return unit; }
    public void setUnit(OrderItemUnit unit) { this.unit = unit; }
    public String getMilestone() { return milestone; }
    public void setMilestone(String milestone) { this.milestone = milestone; }
    public LocalDateTime getDueAt() { return dueAt; }
    public void setDueAt(LocalDateTime dueAt) { this.dueAt = dueAt; }
    public boolean isNotified() { return notified; }
    public void setNotified(boolean notified) { this.notified = notified; }
}
