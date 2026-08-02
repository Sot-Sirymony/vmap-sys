package com.visionmapping.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Background execution, currently used only to get email sending off the request
 * thread.
 *
 * Sending a password reset is slow — an SMTP conversation with a remote server —
 * and doing it inline makes {@code /api/auth/forgot-password} take visibly longer
 * for an address that has an account than for one that does not. That timing
 * difference is a membership oracle, and it defeats the whole reason that
 * endpoint answers 204 either way. Measured locally without SMTP the gap was
 * already 5ms against 2ms; real sending would widen it to hundreds.
 *
 * The pool is small on purpose: this instance has 512MB and half a CPU, and
 * every thread here costs stack space that the Tomcat cap in application.yml is
 * already rationing. Two threads with a bounded queue is enough for password
 * resets, which are rare.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String MAIL_EXECUTOR = "mailExecutor";

    @Bean(MAIL_EXECUTOR)
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("mail-");
        // If the queue ever fills, run the send on the calling thread rather than
        // dropping it. A slow response is recoverable; a silently discarded reset
        // email is a user who can never get back into their account.
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        // Let a send in flight finish when the app is asked to stop, so a deploy
        // does not swallow a reset email that was already promised a 204.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();
        return executor;
    }
}
