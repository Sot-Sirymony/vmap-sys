package com.visionmapping.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** V32 phase 4 (BRD 32 D-32-11, D-32-12): one Streamline suggestion for a goal or step, by its rule (or AI_PICK). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "streamline_nudges")
public class StreamlineNudge extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "anchor_type", nullable = false, length = 4)
    private String anchorType;

    @Column(name = "anchor_id", nullable = false)
    private Long anchorId;

    @Column(nullable = false, length = 30)
    private String rule;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;
}
