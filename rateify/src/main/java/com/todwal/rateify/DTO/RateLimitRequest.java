package com.todwal.rateify.DTO;

import jakarta.validation.constraints.NotBlank;

public record RateLimitRequest(@NotBlank String key, @NotBlank String policyId) {}
