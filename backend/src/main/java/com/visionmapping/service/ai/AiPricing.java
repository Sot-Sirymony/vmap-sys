package com.visionmapping.service.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * AI-NFR-15: US dollars per million tokens, from Anthropic's price list
 * (October 2026), used to count spend against the monthly budget. A model not
 * listed, such as one a refusal fell back to, is priced at the top rate so the
 * budget is never under-counted.
 */
public final class AiPricing {

    private record Rate(BigDecimal input, BigDecimal output) {
    }

    private static final Map<String, Rate> RATES = Map.of(
            "claude-opus-5-5", rate("4.00", "20.00"),
            "claude-sonnet-5-5", rate("2.00", "10.00"),
            "claude-haiku-5-5", rate("0.10", "0.50"),
            "claude-opus-5", rate("5.00", "25.00"),
            "claude-opus-4-8", rate("5.00", "25.00"),
            "claude-sonnet-5", rate("2.00", "10.00"));
    private static final Rate HIGHEST = rate("10.00", "50.00");
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    private AiPricing() {
    }

    public static BigDecimal cost(String model, long inputTokens, long outputTokens) {
        Rate rate = RATES.getOrDefault(model, HIGHEST);
        return rate.input().multiply(BigDecimal.valueOf(inputTokens))
                .add(rate.output().multiply(BigDecimal.valueOf(outputTokens)))
                .divide(MILLION, 6, RoundingMode.HALF_UP);
    }

    private static Rate rate(String input, String output) {
        return new Rate(new BigDecimal(input), new BigDecimal(output));
    }
}
