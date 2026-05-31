package com.todwal.rateify.Controller;

import com.todwal.rateify.Constants.Tiers;
import com.todwal.rateify.DTO.RateLimitRequest;
import com.todwal.rateify.DTO.RateLimitResponse;
import com.todwal.rateify.DTO.RateLimiterDTO;
import com.todwal.rateify.PolicyRegistry;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class RateLimiterController {

    private final PolicyRegistry registry;

    public RateLimiterController(PolicyRegistry registry) {
        this.registry = registry;
    }

    @PostMapping("/check")
    public ResponseEntity<RateLimitResponse> check(@RequestBody @Valid RateLimitRequest request) {
        Tiers tier;
        try {
            tier = Tiers.valueOf(request.policyId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        RateLimiterDTO result = registry.check(request.key(), tier);
        RateLimitResponse body = new RateLimitResponse(
                result.isAllowed(),
                result.getRemainingToken(),
                result.getResetAtEpochMs()
        );

        if (result.isAllowed()) {
            return ResponseEntity.ok(body);
        }

        // RFC 6585 §4 — Retry-After is in whole seconds, ceiling to avoid under-waiting
        long retryAfterSeconds = (result.getRetryAfterMs() + 999) / 1000;
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(retryAfterSeconds))
                .body(body);
    }
}
