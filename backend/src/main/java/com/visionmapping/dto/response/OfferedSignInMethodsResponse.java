package com.visionmapping.dto.response;

/** V24 (SA-FR-03): which sign-in methods the Sign In, Sign Up and invitation pages offer. */
public record OfferedSignInMethodsResponse(boolean password, boolean google) {
}
