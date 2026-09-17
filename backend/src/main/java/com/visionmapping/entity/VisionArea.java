package com.visionmapping.entity;

import com.visionmapping.entity.enums.LifecycleStatus;
import com.visionmapping.entity.enums.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "vision_areas")
public class VisionArea extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 2000)
    private String description;

    // FR-33: "what does this area look like when it's going well?" — optional
    // coaching prompt, never required to save.
    @Column(name = "vision_statement", length = 3000)
    private String visionStatement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Priority priority;

    // Same pattern as Dream.letterRank (FR-57): an optional single
    // uppercase letter, a label not a strict order — ties allowed, never
    // gates anything.
    @Column(name = "letter_rank", length = 1)
    private String letterRank;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private LifecycleStatus status;

    // Display order among this user's vision areas, for drag-and-drop
    // reorder on the Vision Areas page. 0-based; null on legacy rows until
    // the first reorder (or a fresh create) sets it.
    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(nullable = false)
    private boolean archived;
}
