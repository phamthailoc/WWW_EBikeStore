package com.ebike.model.entity.order;

import com.ebike.model.entity.admin.Promotion;
import com.ebike.model.entity.auth.User;
import com.ebike.listener.notification.OrderNotificationJpaListener;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "orders")
@EntityListeners(OrderNotificationJpaListener.class)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** The discount code this order used, so a customer can use a code once and a cancelled order frees it. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;

    @Column(name = "order_number", nullable = false, unique = true, length = 50)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status = OrderStatus.PENDING;

    @Transient
    private OrderStatus previousStatusForNotification;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "shipping_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "registration_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal registrationFee = BigDecimal.ZERO;

    @Column(name = "include_registration_service", nullable = false)
    private Boolean includeRegistrationService = Boolean.FALSE;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "customer_email", length = 255)
    private String customerEmail;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "cancellation_review_note", columnDefinition = "TEXT")
    private String cancellationReviewNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_requested_from_status", length = 30)
    private OrderStatus cancellationRequestedFromStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancellation_requested_by")
    private User cancellationRequestedBy;

    @Column(name = "cancellation_requested_at")
    private LocalDateTime cancellationRequestedAt;

    @Column(name = "cancellation_reviewed_at")
    private LocalDateTime cancellationReviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<OrderItem> items = new LinkedHashSet<>();

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private Payment payment;

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private Shipment shipment;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @PostLoad
    void rememberNotificationSnapshot() {
        previousStatusForNotification = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Promotion getPromotion() {
        return promotion;
    }

    public void setPromotion(Promotion promotion) {
        this.promotion = promotion;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public OrderStatus getPreviousStatusForNotification() {
        return previousStatusForNotification;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getShippingFee() {
        return shippingFee;
    }

    public void setShippingFee(BigDecimal shippingFee) {
        this.shippingFee = shippingFee;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getRegistrationFee() {
        return registrationFee;
    }

    public void setRegistrationFee(BigDecimal registrationFee) {
        this.registrationFee = registrationFee;
    }

    public Boolean getIncludeRegistrationService() {
        return includeRegistrationService;
    }

    public void setIncludeRegistrationService(Boolean includeRegistrationService) {
        this.includeRegistrationService = includeRegistrationService;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public String getCancellationReviewNote() {
        return cancellationReviewNote;
    }

    public void setCancellationReviewNote(String cancellationReviewNote) {
        this.cancellationReviewNote = cancellationReviewNote;
    }

    public OrderStatus getCancellationRequestedFromStatus() {
        return cancellationRequestedFromStatus;
    }

    public void setCancellationRequestedFromStatus(OrderStatus cancellationRequestedFromStatus) {
        this.cancellationRequestedFromStatus = cancellationRequestedFromStatus;
    }

    public User getCancellationRequestedBy() {
        return cancellationRequestedBy;
    }

    public void setCancellationRequestedBy(User cancellationRequestedBy) {
        this.cancellationRequestedBy = cancellationRequestedBy;
    }

    public LocalDateTime getCancellationRequestedAt() {
        return cancellationRequestedAt;
    }

    public void setCancellationRequestedAt(LocalDateTime cancellationRequestedAt) {
        this.cancellationRequestedAt = cancellationRequestedAt;
    }

    public LocalDateTime getCancellationReviewedAt() {
        return cancellationReviewedAt;
    }

    public void setCancellationReviewedAt(LocalDateTime cancellationReviewedAt) {
        this.cancellationReviewedAt = cancellationReviewedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Set<OrderItem> getItems() {
        return items;
    }

    public void setItems(Set<OrderItem> items) {
        this.items = items;
    }

    public Payment getPayment() {
        return payment;
    }

    public void setPayment(Payment payment) {
        this.payment = payment;
    }

    public Shipment getShipment() {
        return shipment;
    }

    public void setShipment(Shipment shipment) {
        this.shipment = shipment;
    }
}
