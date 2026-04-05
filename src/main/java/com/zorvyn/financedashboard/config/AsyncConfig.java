package com.zorvyn.financedashboard.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * ============================================================================
 * ASYNC CONFIGURATION — Background Task Execution Pool
 * ============================================================================
 *
 * Configures the thread pool for @Async methods (e.g., high-value
 * transaction alerts).
 *
 * Why a custom executor instead of relying on Spring's default?
 *
 *   Spring's default async executor is SimpleAsyncTaskExecutor, which
 *   creates a NEW thread for every @Async call. This is dangerous:
 *   - No thread reuse → memory waste
 *   - No backpressure → if async tasks pile up, the JVM runs out of threads
 *   - No monitoring → you can't tell how many tasks are queued
 *
 *   Our custom ThreadPoolTaskExecutor provides:
 *   - BOUNDED thread pool (core=4, max=8) prevents thread explosion
 *   - BOUNDED queue (capacity=100) provides backpressure
 *   - CallerRunsPolicy ensures tasks are never silently dropped
 *
 * Thread Pool Sizing Rationale:
 *   - Core: 4 threads (assumes ~4 CPU cores; IO-bound tasks can go higher)
 *   - Max: 8 threads (2x core for burst handling)
 *   - Queue: 100 tasks (buffer between core and max)
 *
 *   Flow: New task arrives →
 *     1. If threads < core (4) → create new thread
 *     2. If threads >= core → add to queue (up to 100)
 *     3. If queue full AND threads < max (8) → create new thread
 *     4. If queue full AND threads >= max → CallerRunsPolicy kicks in
 *        (the calling thread executes the task synchronously)
 *
 * CallerRunsPolicy:
 *   This is critical. The alternatives are:
 *   - AbortPolicy: throws exception (task is lost)
 *   - DiscardPolicy: silently drops the task (data loss!)
 *   - DiscardOldestPolicy: drops the oldest queued task (also data loss!)
 *   - CallerRunsPolicy: the servlet thread executes the task (slightly slower
 *     response, but NO data loss). This is the safest choice for a financial
 *     system where losing an alert is unacceptable.
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("fd-async-");

        /*
         * CRITICAL: CallerRunsPolicy ensures NO tasks are ever dropped.
         * If the thread pool and queue are full, the calling thread
         * (usually the servlet thread handling the HTTP request) will
         * execute the async task synchronously. This degrades performance
         * gracefully instead of silently losing work.
         */
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        /*
         * WaitForTasksToCompleteOnShutdown ensures that when the application
         * shuts down (gracefully), it waits for in-flight async tasks to finish
         * rather than interrupting them. Combined with Spring Boot's
         * server.shutdown=graceful, this prevents partial alert processing.
         */
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        return executor;
    }
}
