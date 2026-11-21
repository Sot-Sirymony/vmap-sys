package com.visionmapping.entity.enums;

/**
 * V14 (GA-FR-30): what a one-time hand-off code does. SIGN_IN opens a session
 * (GA-FR-19, 60 seconds); LINK lets the person confirm their password to link
 * Google to an existing account (GA-FR-15, 10 minutes).
 */
public enum OAuthHandoffKind {
    SIGN_IN,
    LINK
}
