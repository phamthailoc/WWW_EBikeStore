package com.ebike.model.entity.installment;

import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * V24: a customer's application to pay one order by (simulated) instalments. The plan is stored as it was shown to the customer, so a later change of
 * the admin's rates never changes it. The staff approve or reject; the down payment is the order's payment row.
 */
@Entity
@Table(name = "installment_applications")
public class InstallmentApplication {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    /** The order was cancelled (by the customer, the staff or the timer) while the application was still open. */
    public static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Only the last four digits of the ID number are kept, the rest is stars; null once the account is erased. */
    @Column(name = "id_number_masked", length = 20)
    private String idNumberMasked;

    /** Declared monthly income in VND; null once the account is erased. */
    @Column(name = "monthly_income", precision = 14, scale = 2)
    private BigDecimal monthlyIncome;

    @Column(name = "order_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal orderTotal;

    @Column(name = "down_payment_percent", nullable = false)
    private Integer downPaymentPercent;

    @Column(name = "down_payment_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal downPaymentAmount;

    @Column(name = "financed_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal financedAmount;

    @Column(name = "term_months", nullable = false)
    private Integer termMonths;

    @Column(name = "annual_rate_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal annualRatePercent;

    @Column(name = "monthly_payment", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyPayment;

    @Column(name = "total_payable", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalPayable;

    @Column(nullable = false, length = 12)
    private String status = PENDING;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getIdNumberMasked() {
        return idNumberMasked;
    }

    public void setIdNumberMasked(String idNumberMasked) {
        this.idNumberMasked = idNumberMasked;
    }

    public BigDecimal getMonthlyIncome() {
        return monthlyIncome;
    }

    public void setMonthlyIncome(BigDecimal monthlyIncome) {
        this.monthlyIncome = monthlyIncome;
    }

    public BigDecimal getOrderTotal() {
        return orderTotal;
    }

    public void setOrderTotal(BigDecimal orderTotal) {
        this.orderTotal = orderTotal;
    }

    public Integer getDownPaymentPercent() {
        return downPaymentPercent;
    }

    public void setDownPaymentPercent(Integer downPaymentPercent) {
        this.downPaymentPercent = downPaymentPercent;
    }

    public BigDecimal getDownPaymentAmount() {
        return downPaymentAmount;
    }

    public void setDownPaymentAmount(BigDecimal downPaymentAmount) {
        this.downPaymentAmount = downPaymentAmount;
    }

    public BigDecimal getFinancedAmount() {
        return financedAmount;
    }

    public void setFinancedAmount(BigDecimal financedAmount) {
        this.financedAmount = financedAmount;
    }

    public Integer getTermMonths() {
        return termMonths;
    }

    public void setTermMonths(Integer termMonths) {
        this.termMonths = termMonths;
    }

    public BigDecimal getAnnualRatePercent() {
        return annualRatePercent;
    }

    public void setAnnualRatePercent(BigDecimal annualRatePercent) {
        this.annualRatePercent = annualRatePercent;
    }

    public BigDecimal getMonthlyPayment() {
        return monthlyPayment;
    }

    public void setMonthlyPayment(BigDecimal monthlyPayment) {
        this.monthlyPayment = monthlyPayment;
    }

    public BigDecimal getTotalPayable() {
        return totalPayable;
    }

    public void setTotalPayable(BigDecimal totalPayable) {
        this.totalPayable = totalPayable;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public User getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(User reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
