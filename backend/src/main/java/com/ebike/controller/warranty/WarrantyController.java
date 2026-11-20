package com.ebike.controller.warranty;

import com.ebike.dto.warranty.WarrantyClaimCreateRequest;
import com.ebike.dto.warranty.WarrantyClaimResponse;
import com.ebike.dto.warranty.WarrantyClaimUpdateRequest;
import com.ebike.dto.warranty.WarrantyLookupResponse;
import com.ebike.dto.warranty.WarrantyUnitResponse;
import com.ebike.service.warranty.WarrantyService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** V20: the warranty book of the signed-in customer, the public frame-number lookup and the staff's request queue. */
@RestController
public class WarrantyController {

    private final WarrantyService warrantyService;

    public WarrantyController(WarrantyService warrantyService) {
        this.warrantyService = warrantyService;
    }

    @GetMapping("/api/v1/warranty/units")
    public List<WarrantyUnitResponse> units(Authentication authentication) {
        return warrantyService.book(authentication.getName());
    }

    @GetMapping("/api/v1/warranty/claims")
    public List<WarrantyClaimResponse> myClaims(Authentication authentication) {
        return warrantyService.claimsOf(authentication.getName());
    }

    @PostMapping("/api/v1/warranty/claims")
    @ResponseStatus(HttpStatus.CREATED)
    public WarrantyClaimResponse createClaim(Authentication authentication, @RequestBody WarrantyClaimCreateRequest request) {
        return warrantyService.createClaim(authentication.getName(), request.unitId(), request.description());
    }

    /** Open to everyone; the answer carries no owner data. */
    @GetMapping("/api/v1/warranty/lookup")
    public WarrantyLookupResponse lookup(@RequestParam("frame") String frame) {
        return warrantyService.lookup(frame);
    }

    @GetMapping("/api/v1/manager/warranty-claims")
    public List<WarrantyClaimResponse> listClaims(@RequestParam(required = false) String status) {
        return warrantyService.listClaims(status);
    }

    @PatchMapping("/api/v1/manager/warranty-claims/{id}")
    public WarrantyClaimResponse updateClaim(@PathVariable Long id, @RequestBody WarrantyClaimUpdateRequest request) {
        return warrantyService.updateClaim(id, request);
    }
}
