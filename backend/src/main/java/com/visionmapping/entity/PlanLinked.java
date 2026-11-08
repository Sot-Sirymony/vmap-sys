package com.visionmapping.entity;

import org.springframework.lang.Nullable;

/**
 * A record that can point at one place in the plan: a vision area, dream,
 * goal, step or task (partners, obstacles, messages, reviews, progress logs).
 * Each implementing entity already has the getters it supports; the rest
 * default to "no link".
 *
 * <p>One interface instead of the same "walk up the links" code written once
 * per entity (BRD 13 EJ-FR-26, Effective Java Items 18, 20 and 23).
 */
public interface PlanLinked {

    @Nullable
    default VisionArea getRelatedVisionArea() {
        return null;
    }

    @Nullable
    default Dream getRelatedDream() {
        return null;
    }

    @Nullable
    default Goal getRelatedGoal() {
        return null;
    }

    @Nullable
    default VisionStep getRelatedStep() {
        return null;
    }

    @Nullable
    default TaskItem getRelatedTask() {
        return null;
    }

    /**
     * The vision area this record belongs to, through its links in a fixed
     * order: area, then task, step, goal and dream. Null when it links to
     * nothing, so it drops out of an area-scoped view.
     */
    @Nullable
    default Long linkedVisionAreaId() {
        if (getRelatedVisionArea() != null) {
            return getRelatedVisionArea().getId();
        }
        if (getRelatedTask() != null) {
            return getRelatedTask().visionAreaId();
        }
        if (getRelatedStep() != null) {
            return getRelatedStep().visionAreaId();
        }
        if (getRelatedGoal() != null) {
            return getRelatedGoal().visionAreaId();
        }
        if (getRelatedDream() != null) {
            return getRelatedDream().visionAreaId();
        }
        return null;
    }
}
