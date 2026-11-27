package com.visionmapping.dto.response;

/**
 * V33 (D-33-7): whether a dream or goal has a linked partner. An answer is
 * always a JSON object, never a bare {@code true}, so it can grow later.
 *
 * @param linked whether at least one active partner is linked
 */
public record LinkedPartnerResponse(boolean linked) {
}
