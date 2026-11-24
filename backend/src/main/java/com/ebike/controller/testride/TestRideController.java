package com.ebike.controller.testride;

import com.ebike.dto.testride.TestRideBacklogResponse;
import com.ebike.dto.testride.TestRideCreateRequest;
import com.ebike.dto.testride.TestRideResponse;
import com.ebike.dto.testride.TestRideSlotResponse;
import com.ebike.dto.testride.TestRideUpdateRequest;
import com.ebike.service.testride.TestRideService;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** V23: test-ride bookings of the signed-in customer (CUSTOMER role only) and the staff's schedule. */
@RestController
public class TestRideController {

    private final TestRideService testRideService;

    public TestRideController(TestRideService testRideService) {
        this.testRideService = testRideService;
    }

    /** The places left change with every booking, so no cache (browser or proxy) may keep the answer. */
    @GetMapping("/api/v1/test-rides/slots")
    public ResponseEntity<List<TestRideSlotResponse>> slots(@RequestParam(required = false) Long showroomId, @RequestParam(required = false) String date) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(testRideService.slots(showroomId, date));
    }

    @GetMapping("/api/v1/test-rides")
    public ResponseEntity<List<TestRideResponse>> mine(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(testRideService.mine(authentication.getName()));
    }

    @PostMapping("/api/v1/test-rides")
    @ResponseStatus(HttpStatus.CREATED)
    public TestRideResponse book(Authentication authentication, @RequestBody TestRideCreateRequest request) {
        return testRideService.book(authentication.getName(), request);
    }

    @PatchMapping("/api/v1/test-rides/{id}/cancel")
    public TestRideResponse cancel(Authentication authentication, @PathVariable Long id) {
        return testRideService.cancelOwn(authentication.getName(), id);
    }

    @GetMapping("/api/v1/manager/test-rides")
    public ResponseEntity<List<TestRideResponse>> schedule(
        @RequestParam(required = false) String date,
        @RequestParam(required = false) Long showroomId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(testRideService.schedule(date, showroomId, status, limit));
    }

    @GetMapping("/api/v1/manager/test-rides/backlog")
    public ResponseEntity<TestRideBacklogResponse> backlog() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(testRideService.backlog());
    }

    @PatchMapping("/api/v1/manager/test-rides/{id}")
    public TestRideResponse update(@PathVariable Long id, @RequestBody TestRideUpdateRequest request) {
        return testRideService.update(id, request);
    }
}
