package com.visionmapping.service.ai;

import com.visionmapping.service.AiSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * V31 R-07 with v2 DC-FR-66: the owner check, with the built-in role words and
 * the roles an admin added in AI settings.
 */
@Component
@RequiredArgsConstructor
public class OwnerRoleCheck {

    private final AiSettingsService settings;

    /** The owner to keep, or null when it is not "Me" or a role. */
    @Nullable
    public String check(@Nullable String owner) {
        return AiOwnerRule.check(owner, AiOwnerRule.parseRoles(settings.current().getOwnerRoles()));
    }

    public boolean stripped(@Nullable String owner) {
        return check(owner) == null;
    }
}
