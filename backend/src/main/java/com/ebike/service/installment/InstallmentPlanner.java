package com.ebike.service.installment;

import com.ebike.common.i18n.RequestLanguage;
import com.ebike.dto.installment.InstallmentConfigResponse;
import com.ebike.dto.installment.InstallmentQuoteResponse;
import com.ebike.dto.installment.InstallmentRequest;
import com.ebike.dto.installment.InstallmentTermResponse;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.installment.InstallmentApplication;
import com.ebike.model.entity.order.Order;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.installment.InstallmentApplicationRepository;
import com.ebike.service.admin.PricingService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * V24: the store's side of a simulated instalment plan that checkout needs: the offer (terms, rates and limits), the quote for an amount, and the
 * checking and recording of an application made together with an order. The staff's decision is {@link InstallmentService}.
 */
@Service
public class InstallmentPlanner {

    private final PricingService pricing;
    private final InstallmentApplicationRepository applications;
    private final UserRepository users;
    private final InstallmentRules.Limits limits;

    public InstallmentPlanner(
        PricingService pricing,
        InstallmentApplicationRepository applications,
        UserRepository users,
        @Value("${app.installment.min-total:5000000}") BigDecimal minTotal,
        @Value("${app.installment.max-total:300000000}") BigDecimal maxTotal,
        @Value("${app.installment.min-income:3000000}") BigDecimal minIncome,
        @Value("${app.installment.max-income:1000000000}") BigDecimal maxIncome,
        @Value("${app.installment.max-payment-percent-of-income:60}") int maxPaymentPercentOfIncome,
        @Value("${app.installment.max-open-per-customer:2}") int maxOpenPerCustomer
    ) {
        this.pricing = pricing;
        this.applications = applications;
        this.users = users;
        this.limits = new InstallmentRules.Limits(minTotal, maxTotal, minIncome, maxIncome,
            BigDecimal.valueOf(maxPaymentPercentOfIncome).movePointLeft(2), maxOpenPerCustomer);
    }

    /** The terms on offer now, in months order (an admin can switch one off in the pricing table). */
    public List<InstallmentTermResponse> terms() {
        return InstallmentRules.TERMS.stream()
            .filter(pricing::installmentTermOffered)
            .map(months -> new InstallmentTermResponse(months, pricing.installmentRate(months)))
            .toList();
    }

    public InstallmentConfigResponse config() {
        return new InstallmentConfigResponse(
            InstallmentRules.MIN_DOWN_PERCENT, InstallmentRules.MAX_DOWN_PERCENT, terms(),
            limits.minTotal(), limits.maxTotal(), limits.minIncome(),
            limits.maxPaymentShareOfIncome().movePointRight(2).intValue(), limits.maxOpenPerCustomer());
    }

    /** The plan for an amount; the percent and the term are checked, the amount only decides {@code eligible}. */
    public InstallmentQuoteResponse quote(BigDecimal amount, Integer downPercent, Integer termMonths) {
        if (amount == null || amount.signum() <= 0) {
            throw bad("Số tiền cần tính trả góp phải lớn hơn 0.", "The amount to finance must be greater than 0.");
        }
        // the quote is public: refuse a number that would make the arithmetic build a huge value before computing anything
        if (!InstallmentRules.isQuotableAmount(amount)) {
            throw bad("Số tiền cần tính trả góp không hợp lệ.", "The amount to finance is not valid.");
        }
        InstallmentRules.Plan plan = planFor(amount, downPercent, termMonths);
        boolean eligible = InstallmentRules.totalInRange(plan.orderTotal(), limits);
        String reason = eligible ? null : RequestLanguage.text(totalOutOfRangeVi(), totalOutOfRangeEn());
        return new InstallmentQuoteResponse(plan.orderTotal(), plan.downPaymentPercent(), plan.downPayment(), plan.financed(), plan.termMonths(),
            plan.annualRatePercent(), plan.totalInterest(), plan.monthlyPayment(), plan.totalPayable(), eligible, reason);
    }

    /** An application checked and priced, not saved yet: the order is saved first, then {@link #record}. */
    public record Prepared(InstallmentRules.Plan plan, String idNumberMasked, BigDecimal monthlyIncome) {
    }

    /**
     * Checks the application that comes with an order for {@code orderTotal}: the customer must be signed in (the caller has checked the role), the
     * plan must be on offer, the ID number and the income must look right, and the payment must fit the income. Locks the customer's row first, so two
     * orders placed at once cannot both slip under the limit of open applications.
     */
    public Prepared prepare(User user, BigDecimal orderTotal, InstallmentRequest request) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                RequestLanguage.text("Vui lòng đăng nhập bằng tài khoản khách hàng để mua trả góp.", "Please sign in with a customer account to pay by instalments."));
        }
        if (request == null) {
            throw bad("Vui lòng nhập thông tin hồ sơ trả góp.", "Please fill in the instalment application.");
        }
        InstallmentRules.Plan plan = planFor(orderTotal, request.downPaymentPercent(), request.termMonths());
        if (!InstallmentRules.totalInRange(plan.orderTotal(), limits)) {
            throw bad(totalOutOfRangeVi(), totalOutOfRangeEn());
        }
        String digits = InstallmentRules.normalizeIdNumber(request.idNumber());
        if (digits == null) {
            throw bad("Số CCCD chưa hợp lệ. Vui lòng nhập 12 chữ số (hoặc 9 chữ số với chứng minh nhân dân cũ).",
                "The ID number is not valid. Please enter 12 digits (or 9 digits for an old identity card).");
        }
        BigDecimal income = request.monthlyIncome();
        if (!InstallmentRules.incomeInRange(income, limits)) {
            throw bad("Thu nhập hằng tháng phải từ " + money("vi", limits.minIncome()) + " đến " + money("vi", limits.maxIncome()) + ".",
                "The monthly income must be between " + money("en", limits.minIncome()) + " and " + money("en", limits.maxIncome()) + ".");
        }
        if (!InstallmentRules.affordable(plan.monthlyPayment(), income, limits)) {
            int percent = limits.maxPaymentShareOfIncome().movePointRight(2).intValue();
            throw bad("Khoản trả hàng tháng " + money("vi", plan.monthlyPayment()) + " vượt " + percent + "% thu nhập bạn khai. Hãy tăng khoản trả trước hoặc chọn kỳ hạn dài hơn.",
                "The monthly payment of " + money("en", plan.monthlyPayment()) + " is more than " + percent + "% of the income you declared. Raise the down payment or choose a longer term.");
        }
        // the customer's row is the lock: AccountErasure takes it first too, so the order of locks is the same everywhere
        users.findLockedById(user.getId());
        if (applications.countOpenOf(user.getId()) >= limits.maxOpenPerCustomer()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, RequestLanguage.text(
                "Bạn đang có tối đa " + limits.maxOpenPerCustomer() + " hồ sơ trả góp chưa hoàn tất. Vui lòng đợi duyệt, thanh toán hoặc hủy bớt đơn trước khi gửi thêm.",
                "You already have " + limits.maxOpenPerCustomer() + " instalment applications in progress. Please wait for a decision, pay or cancel an order before applying again."));
        }
        return new Prepared(plan, InstallmentRules.maskIdNumber(digits), income);
    }

    /** Saves the application of an order that has just been saved. */
    public InstallmentApplication record(Order order, User user, Prepared prepared) {
        InstallmentRules.Plan plan = prepared.plan();
        InstallmentApplication application = new InstallmentApplication();
        application.setOrder(order);
        application.setUser(user);
        application.setIdNumberMasked(prepared.idNumberMasked());
        application.setMonthlyIncome(prepared.monthlyIncome());
        application.setOrderTotal(plan.orderTotal());
        application.setDownPaymentPercent(plan.downPaymentPercent());
        application.setDownPaymentAmount(plan.downPayment());
        application.setFinancedAmount(plan.financed());
        application.setTermMonths(plan.termMonths());
        application.setAnnualRatePercent(plan.annualRatePercent());
        application.setMonthlyPayment(plan.monthlyPayment());
        application.setTotalPayable(plan.totalPayable());
        return applications.save(application);
    }

    private InstallmentRules.Plan planFor(BigDecimal amount, Integer downPercent, Integer termMonths) {
        if (downPercent == null || !InstallmentRules.isValidDownPercent(downPercent)) {
            throw bad("Khoản trả trước phải từ " + InstallmentRules.MIN_DOWN_PERCENT + "% đến " + InstallmentRules.MAX_DOWN_PERCENT + "% giá trị đơn.",
                "The down payment must be " + InstallmentRules.MIN_DOWN_PERCENT + "% to " + InstallmentRules.MAX_DOWN_PERCENT + "% of the order.");
        }
        if (termMonths == null || !InstallmentRules.isOfferedTerm(termMonths) || !pricing.installmentTermOffered(termMonths)) {
            throw bad("Kỳ hạn trả góp này hiện không được áp dụng. Vui lòng chọn kỳ hạn khác.",
                "This instalment term is not offered now. Please choose another term.");
        }
        return InstallmentRules.plan(amount, downPercent, termMonths, pricing.installmentRate(termMonths));
    }

    private String totalOutOfRangeVi() {
        return "Trả góp chỉ áp dụng cho đơn từ " + money("vi", limits.minTotal()) + " đến " + money("vi", limits.maxTotal()) + ".";
    }

    private String totalOutOfRangeEn() {
        return "Instalments are available for orders from " + money("en", limits.minTotal()) + " to " + money("en", limits.maxTotal()) + ".";
    }

    private static String money(String language, BigDecimal amount) {
        return InstallmentMessages.money(language, amount);
    }

    private static ResponseStatusException bad(String vietnamese, String english) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, RequestLanguage.text(vietnamese, english));
    }
}
