package com.visionmapping.config;

/** Lets tests in other packages call the package-private start-up check. */
public final class SecurityConfigAccess {

    private SecurityConfigAccess() {
    }

    public static void checkBcryptCost(int cost, String[] activeProfiles) {
        SecurityConfig.checkBcryptCost(cost, activeProfiles);
    }
}
