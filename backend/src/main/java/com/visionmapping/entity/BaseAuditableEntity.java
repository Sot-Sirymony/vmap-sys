package com.visionmapping.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.Getter;
import lombok.Setter;

/**
 * Gives every entity its {@code createdAt} and {@code updatedAt} times, set by
 * JPA when a row is first saved and each time it changes. Times are cut to whole
 * microseconds, the most the database keeps, so a saved time reads back unchanged.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseAuditableEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = now();
    }

    /** Whole microseconds, the most the database keeps, so a saved time reads back unchanged (see TimeConfig). */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
