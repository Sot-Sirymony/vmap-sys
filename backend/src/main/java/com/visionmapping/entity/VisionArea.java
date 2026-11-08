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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A major life or work area, such as Career or Health: the top of the method's
 * hierarchy (Vision Area → Dream → Goal → Step → Task). Its status and archive
 * flag change only through {@link #changeStatus}, {@link #archive} and
 * {@link #restore}, so a restored area always comes back as Paused.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
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
    @Setter(AccessLevel.NONE)
    private LifecycleStatus status;

    // Display order among this user's vision areas, for drag-and-drop
    // reorder on the Vision Areas page. 0-based; null on legacy rows until
    // the first reorder (or a fresh create) sets it.
    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(nullable = false)
    @Setter(AccessLevel.NONE)
    private boolean archived;

    /** A status the participant chose. The decision gate is checked by the service. */
    public void changeStatus(LifecycleStatus newStatus) {
        status = Objects.requireNonNull(newStatus, "status");
    }

    /** Rule 12: hidden, never deleted, and marked Archived. Children are archived by ArchiveCascade. */
    public void archive() {
        status = LifecycleStatus.ARCHIVED;
        archived = true;
    }

    /**
     * Brings an archived vision area back. Its status before the archive is not kept,
     * so it returns as Paused rather than silently Active.
     */
    public void restore() {
        if (!archived) {
            return;
        }
        archived = false;
        if (status == LifecycleStatus.ARCHIVED) {
            status = LifecycleStatus.PAUSED;
        }
    }
}
