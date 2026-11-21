package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.time.Instant;
import java.util.List;

/**
 * V14 (GA-FR-26, GA-FR-27, D-14-3): how the signed-in person can sign in.
 * {@code passwordSet} is false for an account created with Google until it sets
 * a password. Linked accounts show the provider, the email and the link date,
 * never the provider's user ID.
 */
public record SignInMethodsResponse(boolean passwordSet, List<LinkedAccount> linkedAccounts) {

    public SignInMethodsResponse {
        linkedAccounts = Copies.list(linkedAccounts);
    }

    public record LinkedAccount(String provider, String email, Instant linkedAt) {
    }
}
