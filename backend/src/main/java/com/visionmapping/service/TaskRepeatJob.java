package com.visionmapping.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * V27 SR-FR-09: creates the next task of each repeating rule once the current one
 * is due. Hourly, so a restart cannot skip a day; each rule runs in its own
 * transaction, so one bad rule cannot stop the rest (SR-NFR-04: safe to run twice).
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TaskRepeatJob {

    private final TaskRepeatRuleService service;

    @Scheduled(cron = "${app.repeat-tasks.cron:0 7 * * * *}")
    public void run() {
        for (Long ruleId : service.activeRuleIds()) {
            try {
                service.runRule(ruleId);
            } catch (RuntimeException exception) {
                log.error("Could not create the next task for repeat rule {}", ruleId, exception);
            }
        }
    }
}
