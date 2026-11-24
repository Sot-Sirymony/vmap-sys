package com.visionmapping.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * V31 R-07 (DC-FR-61): how many owners one AI answer gave, and how many the
 * server removed because they were not "Me" or a role. Counts only (DCM-07).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_owner_strips")
public class AiOwnerStrip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 40)
    private String feature;

    @Column(name = "owners_seen", nullable = false)
    private int ownersSeen;

    @Column(name = "owners_stripped", nullable = false)
    private int ownersStripped;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
