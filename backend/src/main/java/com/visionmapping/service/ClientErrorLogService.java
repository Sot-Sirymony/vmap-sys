package com.visionmapping.service;

import com.visionmapping.dto.request.ClientErrorLogRequest;
import com.visionmapping.dto.response.PagedResponse;
import com.visionmapping.dto.response.ClientErrorLogResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.ClientErrorLog;
import com.visionmapping.repository.ClientErrorLogRepository;
import com.visionmapping.security.Permission;
import com.visionmapping.security.PermissionService;
import com.visionmapping.service.support.EntityLookup;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Diagnostics: requests that failed in the browser. A page records its own
 * failures, the user sees them on that page, and admins see everyone's. Each
 * one is also written to the server log as "CLIENT ERROR", so it shows in the
 * hosting provider's logs too.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientErrorLogService {

    /** Per user per hour. A page stuck in a failing loop must not fill the table. */
    static final int MAX_PER_HOUR = 100;
    static final int MAX_MESSAGE = 500;
    static final int MAX_PAGE_SIZE = 100;

    private final ClientErrorLogRepository repository;
    private final EntityLookup lookup;
    private final Clock clock;
    private final PermissionService permissions;

    /** Records one failure. Over the hourly limit it is dropped quietly: the log is a help, never a blocker. */
    @Transactional
    public void record(ClientErrorLogRequest request) {
        Long userId = lookup.userId();
        Instant now = clock.instant();
        if (repository.countByUserIdAndOccurredAtAfter(userId, now.minus(Duration.ofHours(1))) >= MAX_PER_HOUR) {
            log.debug("Client error log limit reached for user {}; dropping one entry", userId);
            return;
        }
        ClientErrorLog saved = repository.save(ClientErrorLog.builder()
                .userId(userId)
                .occurredAt(now)
                .page(request.page())
                .method(request.method())
                .path(withoutQuery(request.path()))
                .status(request.status())
                .message(cut(request.message(), MAX_MESSAGE))
                .reference(blankToNull(request.reference()))
                .appVersion(blankToNull(request.appVersion()))
                .build());
        log.warn("CLIENT ERROR page={} {} {} -> {} ref={} user={} message={}",
                saved.getPage(), saved.getMethod(), oneLine(saved.getPath()), saved.getStatus(),
                oneLine(saved.getReference()), userId, oneLine(saved.getMessage()));
    }

    /** The caller's own failures on one page, newest first (at most 50). */
    @Transactional(readOnly = true)
    public List<ClientErrorLogResponse> listMine(String page) {
        return repository.findTop50ByUserIdAndPageOrderByOccurredAtDescIdDesc(lookup.userId(), page).stream()
                .map(entry -> toResponse(entry, false))
                .toList();
    }

    /** Everyone's failures, for admins, optionally for one page or one status. */
    @Transactional(readOnly = true)
    public PagedResponse<ClientErrorLogResponse> search(@Nullable String page, @Nullable Integer status, Pageable pageable) {
        AppUser user = lookup.currentUser();
        permissions.require(user, Permission.HANDLE_SUPPORT, "Only an administrator can read the failed request log.");
        // V33 (D-33-9): newest first is the log's only order; the page size is at most MAX_PAGE_SIZE.
        int pageSize = Math.max(1, Math.min(pageable.getPageSize(), MAX_PAGE_SIZE));
        Page<ClientErrorLog> found = repository.search(blankToNull(page), status, PageRequest.of(pageable.getPageNumber(), pageSize));
        return PagedResponse.from(found, entry -> toResponse(entry, true));
    }

    private static ClientErrorLogResponse toResponse(ClientErrorLog entry, boolean withUser) {
        return new ClientErrorLogResponse(entry.getId(), entry.getOccurredAt(), withUser ? entry.getUserId() : null,
                entry.getPage(), entry.getMethod(), entry.getPath(), entry.getStatus(), entry.getMessage(),
                entry.getReference(), entry.getAppVersion());
    }

    /** Query strings can carry search text or tokens; only the path is kept. */
    static String withoutQuery(String path) {
        int cut = path.indexOf('?');
        return cut < 0 ? path : path.substring(0, cut);
    }

    @Nullable
    private static String cut(@Nullable String text, int max) {
        String value = blankToNull(text);
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    @Nullable
    private static String blankToNull(@Nullable String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    /** Keeps one entry on one log line, whatever the browser sent. */
    private static String oneLine(@Nullable String text) {
        return text == null ? "-" : text.replaceAll("[\\r\\n\\t]", " ");
    }
}
