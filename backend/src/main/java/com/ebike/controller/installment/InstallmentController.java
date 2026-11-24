package com.ebike.controller.installment;

import com.ebike.dto.installment.InstallmentApplicationResponse;
import com.ebike.dto.installment.InstallmentBacklogResponse;
import com.ebike.dto.installment.InstallmentConfigResponse;
import com.ebike.dto.installment.InstallmentDecisionRequest;
import com.ebike.dto.installment.InstallmentQuoteResponse;
import com.ebike.service.installment.InstallmentPlanner;
import com.ebike.service.installment.InstallmentService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * V24: the simulated instalment plan. The offer and the quote are public (a visitor may try the calculator); an application belongs to the order it was made
 * with and is read by its customer or the staff; only the staff decide. Rates change in the admin's pricing table, so no answer may be cached.
 */
@RestController
public class InstallmentController {

    private final InstallmentPlanner planner;
    private final InstallmentService service;

    public InstallmentController(InstallmentPlanner planner, InstallmentService service) {
        this.planner = planner;
        this.service = service;
    }

    @GetMapping("/api/v1/installments/config")
    public ResponseEntity<InstallmentConfigResponse> config() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(planner.config());
    }

    @GetMapping("/api/v1/installments/quote")
    public ResponseEntity<InstallmentQuoteResponse> quote(
        @RequestParam BigDecimal amount,
        @RequestParam Integer downPaymentPercent,
        @RequestParam Integer termMonths
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(planner.quote(amount, downPaymentPercent, termMonths));
    }

    @GetMapping("/api/v1/installments/orders/{orderId}")
    public ResponseEntity<InstallmentApplicationResponse> ofOrder(@PathVariable Long orderId, Authentication authentication) {
        InstallmentApplicationResponse application = service.forOrder(orderId, authentication);
        // 204 for an order that is not paid by instalments (or is not the caller's): every order page asks, so this is not an error
        return application == null
            ? ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()
            : ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(application);
    }

    @GetMapping("/api/v1/installments/mine")
    public ResponseEntity<List<InstallmentApplicationResponse>> mine(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.mine(authentication));
    }

    @GetMapping("/api/v1/manager/installments")
    public ResponseEntity<List<InstallmentApplicationResponse>> queue(@RequestParam(required = false) String status, @RequestParam(required = false) Integer limit, Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.queue(status, authentication, limit));
    }

    @GetMapping("/api/v1/manager/installments/backlog")
    public ResponseEntity<InstallmentBacklogResponse> backlog() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.backlog());
    }

    @PatchMapping("/api/v1/manager/installments/{id}")
    public InstallmentApplicationResponse decide(@PathVariable Long id, @RequestBody InstallmentDecisionRequest request, Authentication authentication) {
        return service.decide(id, request, authentication);
    }
}
