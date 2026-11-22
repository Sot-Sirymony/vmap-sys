package com.visionmapping.entity.enums;

/**
 * V21 (D-21-1): the second factor an account uses. Admins and coaches may only
 * use TOTP (an authenticator app); participants may choose EMAIL.
 */
public enum TwoFactorMethod {
    TOTP,
    EMAIL
}
