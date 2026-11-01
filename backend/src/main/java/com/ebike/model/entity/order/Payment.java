package com.ebike.model.entity.order;

import com.ebike.listener.notification.PaymentNotificationJpaListener;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@EntityListeners(PaymentNotificationJpaListener.class)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Transient
    private PaymentStatus previousPaymentStatusForNotification;

    @Column(name = "transaction_reference", length = 100)
    private String transactionReference;

    @Column(length = 10)
    private String currency = "VND";

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "provider_txn_id", length = 100)
    private String providerTxnId;

    @Column(name = "provider_response", columnDefinition = "TEXT")
    private String providerResponse;

    @Column(name = "vnpay_response_code", length = 10)
    private String vnpayResponseCode;

    @Column(name = "vnpay_bank_code", length = 30)
    private String vnpayBankCode;

    @Column(name = "vnpay_card_type", length = 30)
    private String vnpayCardType;

    @Column(name = "vnpay_secure_hash", length = 256)
    private String vnpaySecureHash;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @PostLoad
    void rememberNotificationSnapshot() {
        previousPaymentStatusForNotification = paymentStatus;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public PaymentStatus getPreviousPaymentStatusForNotification() {
        return previousPaymentStatusForNotification;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getProviderTxnId() {
        return providerTxnId;
    }

    public void setProviderTxnId(String providerTxnId) {
        this.providerTxnId = providerTxnId;
    }

    public String getProviderResponse() {
        return providerResponse;
    }

    public void setProviderResponse(String providerResponse) {
        this.providerResponse = providerResponse;
    }

    public String getVnpayResponseCode() {
        return vnpayResponseCode;
    }

    public void setVnpayResponseCode(String vnpayResponseCode) {
        this.vnpayResponseCode = vnpayResponseCode;
    }

    public String getVnpayBankCode() {
        return vnpayBankCode;
    }

    public void setVnpayBankCode(String vnpayBankCode) {
        this.vnpayBankCode = vnpayBankCode;
    }

    public String getVnpayCardType() {
        return vnpayCardType;
    }

    public void setVnpayCardType(String vnpayCardType) {
        this.vnpayCardType = vnpayCardType;
    }

    public String getVnpaySecureHash() {
        return vnpaySecureHash;
    }

    public void setVnpaySecureHash(String vnpaySecureHash) {
        this.vnpaySecureHash = vnpaySecureHash;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }
}
