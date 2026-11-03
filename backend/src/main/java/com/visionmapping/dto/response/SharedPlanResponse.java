package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.WorkStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * V9 (v0.1 FR-10; AI-NFR-04): what a partner sees through a share link. Only
 * the shared dream's plan and the owner's first name; no journals, tasks,
 * owners, partner details or anything from other dreams.
 */
public record SharedPlanResponse(
        String ownerFirstName,
        String partnerName,
        String message,
        String dreamTitle,
        String dreamDescription,
        String successDefinition,
        LocalDate targetDate,
        BigDecimal progressPercent,
        Instant expiresAt,
        List<SharedGoal> goals) {

    public record SharedGoal(String title, WorkStatus status, LocalDate targetDate, BigDecimal progressPercent,
                             List<SharedStep> steps) {
    }

    public record SharedStep(String title, WorkStatus status, LocalDate targetDate) {
    }
}
