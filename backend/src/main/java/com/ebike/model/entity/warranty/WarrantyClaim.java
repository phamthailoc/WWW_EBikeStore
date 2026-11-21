package com.ebike.model.entity.warranty;

import com.ebike.model.entity.auth.User;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** V20: a customer's request to have a delivered vehicle looked at under warranty; the staff move it NEW to DONE or REJECTED. */
@Entity
@Table(name = "warranty_claims")
public class WarrantyClaim {

    public static final String NEW = "NEW";
    public static final String RECEIVED = "RECEIVED";
    public static final String REPAIRING = "REPAIRING";
    public static final String DONE = "DONE";
    public static final String REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_unit_id", nullable = false)
    private OrderItemUnit unit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String description;

    @Column(nullable = false, length = 30)
    private String status = NEW;

    /** True only when the warranty had ended when the request was sent: the repair is then charged. A bike with no warranty length on record (UNKNOWN) is not charged. */
    @Column(name = "out_of_warranty", nullable = false)
    private boolean outOfWarranty;

    @Column(name = "staff_note", columnDefinition = "LONGTEXT")
    private String staffNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public OrderItemUnit getUnit() { return unit; }
    public void setUnit(OrderItemUnit unit) { this.unit = unit; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isOutOfWarranty() { return outOfWarranty; }
    public void setOutOfWarranty(boolean outOfWarranty) { this.outOfWarranty = outOfWarranty; }
    public String getStaffNote() { return staffNote; }
    public void setStaffNote(String staffNote) { this.staffNote = staffNote; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
