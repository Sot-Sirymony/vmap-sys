package com.visionmapping.security.audit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.visionmapping.repository.SecurityEventRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

/** BRD 13 EJ-FR-25: a failed audit write is logged and never fails the user's request. */
@ExtendWith(MockitoExtension.class)
class SecurityEventServiceFailureTest {

    @Mock private SecurityEventRepository repository;
    @Mock private SecurityAlertService alerts;
    @Mock private PlatformTransactionManager transactionManager;

    @Test
    void aFailedWriteDoesNotReachTheCaller() {
        SecurityEventService service = new SecurityEventService(repository, alerts, Clock.systemUTC(), transactionManager, 365);
        when(repository.save(any())).thenThrow(new IllegalStateException("database down"));

        assertThatCode(() -> service.record(SecurityEventType.values()[0], SecurityOutcome.SUCCESS, "dream", 1L, "detail"))
                .doesNotThrowAnyException();
        verifyNoInteractions(alerts);
    }
}
