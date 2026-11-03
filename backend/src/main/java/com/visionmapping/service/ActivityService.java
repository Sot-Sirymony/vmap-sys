package com.visionmapping.service;

import com.visionmapping.entity.AppUser;
import com.visionmapping.repository.AttitudeCheckInRepository;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.repository.VisionStepRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * When a user last did something: changed a step or task, or checked in. Used
 * by reminders (FR-77) and by the coach's at-risk flag (V9, v0.1 FR-17), so
 * both mean the same thing by "inactive".
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ActivityService {

    private final VisionStepRepository visionStepRepository;
    private final TaskItemRepository taskItemRepository;
    private final AttitudeCheckInRepository attitudeCheckInRepository;

    /** The latest activity, or the day the account was created when there is none. */
    public Instant lastActivity(AppUser user) {
        return Stream.of(
                        visionStepRepository.findLastUpdatedAt(user.getId()),
                        taskItemRepository.findLastUpdatedAt(user.getId()),
                        attitudeCheckInRepository.findLastUpdatedAt(user.getId()))
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(user.getCreatedAt());
    }
}
