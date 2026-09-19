package com.meridiantrust.sentinel.common.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Bounded executor backing parallel bulk detection.
 *
 * <p>NFR (Performance): bulk evaluation is chunked across this pool so 10,000
 * transactions complete well inside the 2-minute budget.
 *
 * <p>NFR (Concurrency): the pool is <em>bounded</em> with a CallerRunsPolicy —
 * under a burst, the submitting thread executes the chunk itself rather than
 * queueing without limit. Ingestion therefore self-throttles instead of
 * exhausting memory, and no chunk is ever silently dropped (which would mean a
 * lost alert).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String DETECTION_EXECUTOR = "detectionExecutor";

    @Bean(name = DETECTION_EXECUTOR)
    public ThreadPoolTaskExecutor detectionExecutor(SentinelProperties props) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.detection().workerThreads());
        executor.setMaxPoolSize(props.detection().workerThreads());
        executor.setQueueCapacity(props.detection().queueCapacity());
        executor.setThreadNamePrefix("sentinel-detect-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        // Carry the correlation id into worker threads so a bulk run's logs
        // remain traceable end to end.
        executor.setTaskDecorator(mdcPropagating());
        executor.initialize();
        return executor;
    }

    private TaskDecorator mdcPropagating() {
        return runnable -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                if (context != null) {
                    MDC.setContextMap(context);
                }
                try {
                    runnable.run();
                } finally {
                    MDC.clear();
                }
            };
        };
    }
}
