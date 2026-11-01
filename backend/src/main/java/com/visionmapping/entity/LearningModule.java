package com.visionmapping.entity;

import com.visionmapping.entity.enums.ModuleCompletionRule;
import com.visionmapping.entity.enums.ModuleStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** FR-72: one of the fifteen modules. Seeded by migration, never written by users. */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "learning_modules")
public class LearningModule {

    @Id
    @Column(name = "module_number")
    private Integer number;

    @Column(nullable = false, unique = true)
    private Integer position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModuleStage stage;

    @Enumerated(EnumType.STRING)
    @Column(name = "completion_rule", nullable = false, length = 30)
    private ModuleCompletionRule completionRule;

    @Column(name = "link_path", length = 120)
    private String linkPath;
}
