package com.ebike.model.entity.order;

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
import java.time.LocalDateTime;

/** One physical vehicle of an order line: its frame number (unique) and engine or motor number (V18). */
@Entity
@Table(name = "order_item_units")
public class OrderItemUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @Column(name = "frame_number", nullable = false, length = 30, unique = true)
    private String frameNumber;

    @Column(name = "engine_number", nullable = false, length = 30)
    private String engineNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public OrderItem getOrderItem() { return orderItem; }

    public void setOrderItem(OrderItem orderItem) { this.orderItem = orderItem; }

    public String getFrameNumber() { return frameNumber; }

    public void setFrameNumber(String frameNumber) { this.frameNumber = frameNumber; }

    public String getEngineNumber() { return engineNumber; }

    public void setEngineNumber(String engineNumber) { this.engineNumber = engineNumber; }
}
