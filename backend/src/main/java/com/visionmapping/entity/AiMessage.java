package com.visionmapping.entity;

import com.visionmapping.entity.enums.AiMessageRole;
import com.visionmapping.entity.enums.AiSafetyFlag;
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

/** AI-FR-25: one message in a Dream Coach conversation. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_messages")
public class AiMessage extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private AiConversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiMessageRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "safety_flag", length = 20)
    private AiSafetyFlag safetyFlag;

    /** AI-FR-05: comma-separated source keys a reply was based on, such as plan,fears. */
    @Column(name = "context_sources", length = 300)
    private String contextSources;

    /** The request that produced an assistant message; its rating lives there. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usage_log_id")
    private AiUsageLog usageLog;
}
