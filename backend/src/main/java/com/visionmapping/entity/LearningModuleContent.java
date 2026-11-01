package com.visionmapping.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** FR-72: a module's text in one language. Users read it; only an admin edits it (FR-78). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "learning_module_content")
public class LearningModuleContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "module_number", nullable = false)
    private Integer moduleNumber;

    @Column(nullable = false, length = 10)
    private String language;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 600)
    private String summary;

    @Column(nullable = false, length = 3000)
    private String guidance;

    @Column(name = "exercise_instructions", nullable = false, length = 1000)
    private String exerciseInstructions;

    @Column(name = "fields_json", length = 2000)
    private String fieldsJson;
}
