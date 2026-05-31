package com.todwal.rateify;

import com.todwal.rateify.Constants.Tiers;
import com.todwal.rateify.DTO.RateLimiterDTO;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class RateifyApplication {

	public static void main(String[] args) {
		SpringApplication.run(RateifyApplication.class, args);
	}

	@Bean
	CommandLineRunner demo(PolicyRegistry registry) {
		return args -> {
			System.out.println("=== Rateify Policy Demo ===\n");

			// FREE_TIER — Token Bucket: 10 capacity, 1 token/sec refill
			System.out.println("-- FREE_TIER (Token Bucket: cap=10, refill=1/s) --");
			for (int i = 1; i <= 3; i++) {
				printResult("free-user", Tiers.FREE_TIER, i, registry.check("free-user", Tiers.FREE_TIER));
			}

			// PREMIUM_TIER — Sliding Window Log: 100 req / 60s
			System.out.println("\n-- PREMIUM_TIER (Sliding Window Log: 100 req/60s) --");
			for (int i = 1; i <= 3; i++) {
				printResult("premium-user", Tiers.PREMIUM_TIER, i, registry.check("premium-user", Tiers.PREMIUM_TIER));
			}

			// LOGIN_TIER — Sliding Window Counter: 5 req / 60s — exhaust the limit
			System.out.println("\n-- LOGIN_TIER (Sliding Window Counter: 5 req/60s) --");
			for (int i = 1; i <= 6; i++) {
				printResult("login-user", Tiers.LOGIN_TIER, i, registry.check("login-user", Tiers.LOGIN_TIER));
			}

			System.out.println("\n=== Demo complete ===");
		};
	}

	private void printResult(String key, Tiers tier, int call, RateLimiterDTO result) {
		System.out.printf("  [%s] call #%d → allowed=%-5s  remaining=%-4d  reason=%s%n",
				tier, call, result.isAllowed(), result.getRemainingToken(), result.getReason());
		if (!result.isAllowed()) {
			System.out.printf("                    retryAfterMs=%d%n", result.getRetryAfterMs());
		}
	}

}
