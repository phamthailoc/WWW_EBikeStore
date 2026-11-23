package com.ebike.controller.savings;

import com.ebike.dto.savings.SavingsResponse;
import com.ebike.service.savings.SavingsService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * V33: the savings calculator of a bike. Open to visitors and to every role that has the product:view permission (it changes nothing); a role without
 * that permission, such as OPERATOR, SUPPORT or MAINTENANCE in the sample data, gets a 403 and the page hides the box. The prices live in the admin's pricing table, so no answer may be cached.
 */
@RestController
public class SavingsController {

    private final SavingsService service;

    public SavingsController(SavingsService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/products/{slug}/savings")
    public ResponseEntity<SavingsResponse> savings(
        @PathVariable String slug,
        @RequestParam(required = false) Integer kmPerDay,
        @RequestParam(required = false) Integer daysPerMonth
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.forProduct(slug, kmPerDay, daysPerMonth));
    }
}
