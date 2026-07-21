package com.visionmapping.entity;

import com.visionmapping.entity.enums.DreamStatus;
import com.visionmapping.entity.enums.DreamType;
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
import java.time.LocalDate;
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
@Table(name = "dreams")
public class Dream extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vision_area_id", nullable = false)
    private VisionArea visionArea;

    @Column(nullable = false, length = 220)
    private String title;

    @Column(length = 3000)
    private String description;

    @Column(name = "why_important", length = 3000)
    private String whyImportant;

    @Column(name = "success_definition", length = 3000)
    private String successDefinition;

    @Enumerated(EnumType.STRING)
    @Column(name = "dream_type", nullable = false, length = 40)
    private DreamType dreamType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Priority priority;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private DreamStatus status;

    // FR-31: aspirational metadata only — never touches progress/completion.
    @Column(nullable = false)
    private boolean moonshot;

    @Column(name = "moonshot_vision", length = 3000)
    private String moonshotVision;

    @Column(nullable = false)
    private boolean archived;
}
