package com.visionmapping.service.ai;

import com.visionmapping.entity.AppUser;

/**
 * V15 (AH-FR-76, AHC-27): the participant's own work style for AI helpers
 * outside the Work Style page. Empty unless the participant has turned on
 * "Let AI helpers use my work style"; even then, only the dominant and
 * secondary style are given, never the pole counts or quiz answers.
 */
final class WorkStyleContext {

    private WorkStyleContext() {
    }

    static String line(AppUser user) {
        if (!user.isAiUseWorkStyle() || user.getWorkStyleDominant() == null) {
            return "";
        }
        return "\nThe participant describes their own work style as " + user.getWorkStyleDominant()
                + (user.getWorkStyleSecondary() == null ? "" : ", with some " + user.getWorkStyleSecondary())
                + ". It is a self-reported tendency: use it only to fit how you phrase help, never to judge or label them.\n";
    }
}
