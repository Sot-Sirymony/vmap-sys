package com.visionmapping.service;

import com.visionmapping.dto.response.StreamlineResponses;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.repository.TaskStatusChangeRepository;
import com.visionmapping.service.support.EntityLookup;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.visionmapping.dto.response.PagedResponse;
import com.visionmapping.util.Pageables;
import java.util.Set;
import org.springframework.data.domain.Pageable;

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

    /** V33 (D-33-10): status changes sort by when they happened. */
    static final Set<String> HISTORY_SORTS = Set.of("changedAt");

    /** V33 (D-33-9): one page of a task's status changes, the caller's own task only. */
    @Transactional(readOnly = true)
    public PagedResponse<StreamlineResponses.StatusChange> pageHistory(Long taskId, Pageable pageable) {
        TaskItem task = lookup.task(taskId);
        return PagedResponse.from(repository.findByTask_IdAndUser_Id(task.getId(), lookup.userId(), Pageables.whitelist(pageable, HISTORY_SORTS)),
                change -> new StreamlineResponses.StatusChange(change.getFromStatus(), change.getToStatus(), change.getChangedAt()));
    }
}
