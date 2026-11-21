package com.visionmapping.service.oauth;

import java.util.Objects;

/**
 * V14 (GA-FR-12, GA-FR-13): what a verified Google ID token says about the
 * person. {@code subject} is Google's permanent user ID and is what identifies
 * the account; {@code email} is verified by Google; {@code name} may be blank.
 */
public record GoogleIdentity(String subject, String email, String name) {

    public GoogleIdentity {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(email, "email");
        name = name == null ? "" : name.trim();
    }
}
