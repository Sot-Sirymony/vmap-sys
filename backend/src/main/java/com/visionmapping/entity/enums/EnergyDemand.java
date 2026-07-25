package com.visionmapping.entity.enums;

/**
 * FR-34.1: how a task affects the user's energy, treated as a finite asset
 * alongside time. Diagnostic metadata only (BR-27) — it never gates saving a
 * task or changing its status. A null value (existing tasks, or a task saved
 * without a choice) reads as {@link #NEUTRAL}.
 */
public enum EnergyDemand {
    CHARGE,
    NEUTRAL,
    DRAIN
}
