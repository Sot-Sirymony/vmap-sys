package com.visionmapping.security.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.repository.SecurityEventRepository;
import com.visionmapping.service.support.EntityLookup;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** BRD 13 EJ-FR-22: an open-ended search runs up to the injected clock's "now", plus a minute. */
@ExtendWith(MockitoExtension.class)
class SecurityLogServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-01T09:00:00Z");

    @Mock private SecurityEventRepository repository;
    @Mock private SecurityAlertService alerts;
    @Mock private EntityLookup lookup;

    @Test
    void anOpenEndedSearchEndsOneMinuteAfterTheInjectedNow() {
        SecurityLogService service = new SecurityLogService(repository, alerts, lookup, Clock.fixed(NOW, ZoneOffset.UTC));
        when(lookup.currentUser()).thenReturn(AppUser.builder().id(1L).role(UserRole.ADMIN).status(UserStatus.ACTIVE).build());
        when(repository.search(isNull(), isNull(), isNull(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());
        when(alerts.recentAlerts()).thenReturn(List.of());

        service.search(null, null, null, null, null, 0, 20);

        ArgumentCaptor<Instant> end = ArgumentCaptor.forClass(Instant.class);
        verify(repository).search(isNull(), isNull(), isNull(), eq(Instant.EPOCH), end.capture(), any(Pageable.class));
        assertThat(end.getValue()).isEqualTo(NOW.plusSeconds(60));
    }
}
