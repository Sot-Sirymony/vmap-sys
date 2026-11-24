package com.visionmapping.service;

import com.visionmapping.dto.response.StreamlineResponses;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.repository.TaskStatusChangeRepository;
import com.visionmapping.service.support.EntityLookup;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** V27: a task's status history (SR-FR-06), newest last. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TaskHistoryService {

    private final EntityLookup lookup;
    private final TaskStatusChangeRepository repository;

    public List<StreamlineResponses.StatusChange> history(Long taskId) {
        TaskItem task = lookup.task(taskId);
        return repository.findByTask_IdAndUser_IdOrderByChangedAtAscIdAsc(task.getId(), lookup.userId()).stream()
                .map(change -> new StreamlineResponses.StatusChange(change.getFromStatus(), change.getToStatus(), change.getChangedAt()))
                .toList();
    }
}
