package com.c203.limit.global.config;

import java.util.Map;
import java.util.concurrent.Executor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /** 조회 폭주가 DB 커넥션과 애플리케이션 메모리를 무제한 점유하지 않게 실행량을 제한한다. */
    @Bean(name = "productViewCountExecutor")
    public Executor productViewCountExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("product-view-count-");
        executor.setTaskDecorator(
                task -> {
                    Map<String, String> callerContext = MDC.getCopyOfContextMap();
                    return () -> {
                        Map<String, String> workerContext = MDC.getCopyOfContextMap();
                        try {
                            if (callerContext == null) MDC.clear();
                            else MDC.setContextMap(callerContext);
                            task.run();
                        } finally {
                            if (workerContext == null) MDC.clear();
                            else MDC.setContextMap(workerContext);
                        }
                    };
                });
        executor.initialize();
        return executor;
    }
}
