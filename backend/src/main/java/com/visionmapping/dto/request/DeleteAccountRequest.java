package com.visionmapping.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Deleting the signed-in account. V14 (GA-FR-32, D-14-3): an account created
 * with Google leaves the password out and relies on the fresh email code; every
 * other account must give its password.
 */
public record DeleteAccountRequest(@Size(max = 100) String password) {
}
