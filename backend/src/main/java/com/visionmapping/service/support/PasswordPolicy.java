package com.visionmapping.service.support;

import com.visionmapping.exception.BusinessRuleException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * V10 (SEC-07-06), following NIST SP 800-63B: a new password is at least 8
 * characters (checked on the request), is not on the list of common and
 * breached passwords, and is not built from the account's own email or the
 * app's name. There are no other composition rules.
 */
@Component
public class PasswordPolicy {

    static final String LIST = "/security/common-passwords.txt";
    private static final int MIN_EMAIL_PART = 4;
    private static final Set<String> APP_WORDS = Set.of("visionmapping", "vision mapping", "visionmap");

    private final Set<String> common;

    public PasswordPolicy() {
        this.common = load();
    }

    /**
     * @param email the account's email, or null when unknown
     * @throws BusinessRuleException when the password is too easy to guess
     */
    public void check(String password, @Nullable String email) {
        String lower = password.toLowerCase(Locale.ROOT);
        if (common.contains(lower)) {
            throw new BusinessRuleException("That password is too common and easy to guess. Choose a longer or less usual one.");
        }
        if (APP_WORDS.stream().anyMatch(lower::contains)) {
            throw new BusinessRuleException("Do not use the app's name in your password.");
        }
        if (email != null) {
            String local = email.toLowerCase(Locale.ROOT).split("@", 2)[0];
            if (local.length() >= MIN_EMAIL_PART && lower.contains(local)) {
                throw new BusinessRuleException("Do not use your email address in your password.");
            }
        }
    }

    boolean isCommon(String password) {
        return common.contains(password.toLowerCase(Locale.ROOT));
    }

    private static Set<String> load() {
        try (InputStream stream = PasswordPolicy.class.getResourceAsStream(LIST)) {
            if (stream == null) {
                // Fail closed at startup rather than run without the check.
                throw new IllegalStateException("Missing " + LIST);
            }
            Set<String> words = new HashSet<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                reader.lines()
                        .map(String::strip)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .map(line -> line.toLowerCase(Locale.ROOT))
                        .forEach(words::add);
            }
            return Set.copyOf(words);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + LIST, exception);
        }
    }
}
