package com.visionmapping.service.support;

import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.TaskStatusChange;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.repository.TaskStatusChangeRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * V27 SR-FR-06: writes one history row whenever a task's status changes. Every
 * path that changes a task's status (create, edit, the status PATCH and board
 * drag, split, progress logs) goes through TaskItemService or ProgressLogService,
 * which call this after the change.
 */
@Component
@RequiredArgsConstructor
public class TaskStatusHistory {

    private final TaskStatusChangeRepository repository;
    private final Clock clock;

    /** Records the move from {@code before} (null for a new task) to the task's current status, if it moved. */
    public void record(TaskItem task, @Nullable WorkStatus before) {
        if (task.getStatus() == null || task.getStatus() == before) {
            return;
        }
        repository.save(TaskStatusChange.builder()
                .user(task.getUser())
                .task(task)
                .fromStatus(before)
                .toStatus(task.getStatus())
                .changedAt(Instant.now(clock))
                .build());
    }
}
