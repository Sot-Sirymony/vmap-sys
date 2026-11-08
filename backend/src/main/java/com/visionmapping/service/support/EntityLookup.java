package com.visionmapping.service.support;

import org.springframework.beans.factory.annotation.Autowired;
import com.visionmapping.security.audit.SecurityOutcome;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityEventService;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.CommunicationMessage;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.Obstacle;
import com.visionmapping.entity.Partner;
import com.visionmapping.entity.ProgressLog;
import com.visionmapping.entity.Review;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionArea;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.repository.CommunicationMessageRepository;
import com.visionmapping.repository.DreamRepository;
import com.visionmapping.repository.GoalRepository;
import com.visionmapping.repository.ObstacleRepository;
import com.visionmapping.repository.PartnerRepository;
import com.visionmapping.repository.ProgressLogRepository;
import com.visionmapping.repository.ReviewRepository;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.repository.VisionAreaRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.util.UserScope;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * One place to resolve an entity by id for the current user. Every finder
 * applies the same ownership check, so no caller can reach another user's data,
 * and the required/optional variants keep the "may be null" decision explicit
 * at the call site instead of scattering null handling through the services.
 */
@Component
@RequiredArgsConstructor
public class EntityLookup {

    @Nullable
    private SecurityEventService securityEvents;

    private final UserScope userScope;
    private final VisionAreaRepository visionAreaRepository;
    private final DreamRepository dreamRepository;
    private final GoalRepository goalRepository;
    private final VisionStepRepository visionStepRepository;
    private final TaskItemRepository taskItemRepository;
    private final PartnerRepository partnerRepository;
    private final CommunicationMessageRepository communicationMessageRepository;
    private final ReviewRepository reviewRepository;
    private final ObstacleRepository obstacleRepository;
    private final ProgressLogRepository progressLogRepository;

    public AppUser currentUser() {
        return userScope.currentUser();
    }

    public Long userId() {
        return userScope.currentUser().getId();
    }

    public VisionArea visionArea(Long id) {
        return owned(visionAreaRepository::findById, e -> e.getUser().getId(), "Vision area", id);
    }

    public Dream dream(Long id) {
        return owned(dreamRepository::findById, e -> e.getUser().getId(), "Dream", id);
    }

    public Goal goal(Long id) {
        return owned(goalRepository::findById, e -> e.getUser().getId(), "Goal", id);
    }

    public VisionStep step(Long id) {
        return owned(visionStepRepository::findById, e -> e.getUser().getId(), "Step", id);
    }

    public TaskItem task(Long id) {
        return owned(taskItemRepository::findById, e -> e.getUser().getId(), "Task", id);
    }

    public Partner partner(Long id) {
        return owned(partnerRepository::findById, e -> e.getUser().getId(), "Partner", id);
    }

    public CommunicationMessage communicationMessage(Long id) {
        return owned(communicationMessageRepository::findById, e -> e.getUser().getId(), "Communication message", id);
    }

    public Review review(Long id) {
        return owned(reviewRepository::findById, e -> e.getUser().getId(), "Review", id);
    }

    public Obstacle obstacle(Long id) {
        return owned(obstacleRepository::findById, e -> e.getUser().getId(), "Obstacle", id);
    }

    public ProgressLog progressLog(Long id) {
        return owned(progressLogRepository::findById, e -> e.getUser().getId(), "Progress log", id);
    }

    /** Null in, null out: maps an optional link id from a request to an optional relation. */
    @Nullable
    public VisionArea optionalVisionArea(@Nullable Long id) {
        return id == null ? null : visionArea(id);
    }

    /** Null in, null out: maps an optional link id from a request to an optional relation. */
    @Nullable
    public Dream optionalDream(@Nullable Long id) {
        return id == null ? null : dream(id);
    }

    /** Null in, null out: maps an optional link id from a request to an optional relation. */
    @Nullable
    public Goal optionalGoal(@Nullable Long id) {
        return id == null ? null : goal(id);
    }

    /** Null in, null out: maps an optional link id from a request to an optional relation. */
    @Nullable
    public VisionStep optionalStep(@Nullable Long id) {
        return id == null ? null : step(id);
    }

    /** Null in, null out: maps an optional link id from a request to an optional relation. */
    @Nullable
    public TaskItem optionalTask(@Nullable Long id) {
        return id == null ? null : task(id);
    }

    /** Null in, null out: maps an optional link id from a request to an optional relation. */
    @Nullable
    public Partner optionalPartner(@Nullable Long id) {
        return id == null ? null : partner(id);
    }

    /**
     * CLAUDE.md Core Business Rules 13 and 14: a record is returned only to its
     * owner; anyone else gets the same 404 as for a record that does not exist.
     * Takes the repository's finder, not an Optional parameter (Item 55).
     */
    private <T> T owned(Function<Long, Optional<T>> finder, Function<T, Long> ownerId, String label, Long id) {
        Supplier<ResourceNotFoundException> notFound =
                () -> new ResourceNotFoundException(label + " not found: " + id);
        T entity = finder.apply(id).orElseThrow(notFound);
        if (!ownerId.apply(entity).equals(userId())) {
            // V10 (SEC-09-01): the caller still gets the same 404 as for a
            // record that does not exist, but the log notes the attempt, and
            // many in a row raise an alert.
            if (securityEvents != null) {
                securityEvents.record(SecurityEventType.NOT_OWNER, SecurityOutcome.DENIED, label, id, null);
            }
            throw notFound.get();
        }
        return entity;
    }

    /**
     * Optional so unit tests can build this class by hand without the log.
     * In the running app Spring always sets it.
     */
    @Autowired(required = false)
    public void setSecurityEvents(SecurityEventService securityEvents) {
        this.securityEvents = securityEvents;
    }
}
