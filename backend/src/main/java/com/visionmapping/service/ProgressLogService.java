package com.visionmapping.service;

import com.visionmapping.config.CacheConfig;
import com.visionmapping.dto.request.ProgressLogRequest;
import com.visionmapping.dto.response.ProgressLogResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.ProgressLog;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.ProgressLogRepository;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.ProgressCalculator;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records point-in-time progress notes against a task. Logging progress also
 * advances the task itself (and rolls the change up its step and goal), so a
 * log is both a history entry and the trigger for a recalculation.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ProgressLogService {

    private final EntityLookup lookup;
    private final ProgressCalculator progress;
    private final VisionMappingMapper mapper;
    private final ProgressLogRepository progressLogRepository;
    private final Clock clock;

    @Cacheable(CacheConfig.PROGRESS_LOG_LIST_CACHE)
    @Transactional(readOnly = true)
    public List<ProgressLogResponse> listProgressLogs() {
        return progressLogRepository.findByUser_IdAndArchivedFalse(lookup.userId()).stream().map(mapper::toResponse).toList();
    }

    public ProgressLogResponse createProgressLog(ProgressLogRequest request) {
        AppUser user = lookup.currentUser();
        TaskItem task = lookup.task(request.relatedTaskId());
        ProgressLog entity = ProgressLog.builder()
                .user(user)
                .relatedTask(task)
                .progressPercentBefore(progress.normalizeProgress(request.progressPercentBefore()))
                .progressPercentAfter(progress.normalizeProgress(request.progressPercentAfter()))
                .note(request.note())
                .loggedAt(Instant.now(clock))
                .build();
        task.recordProgress(entity.getProgressPercentAfter(), clock);
        progress.recalculateStep(task.getStep());
        return mapper.toResponse(progressLogRepository.save(entity));
    }

    @Cacheable(CacheConfig.PROGRESS_LOG_CACHE)
    @Transactional(readOnly = true)
    public ProgressLogResponse getProgressLog(Long id) {
        return mapper.toResponse(lookup.progressLog(id));
    }

    public void archiveProgressLog(Long id) {
        lookup.progressLog(id).setArchived(true);
    }
}
