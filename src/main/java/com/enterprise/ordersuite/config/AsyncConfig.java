package com.enterprise.ordersuite.config;

import com.enterprise.ordersuite.common.tenancy.ContextCopyingTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {

    // Spring Boot applies a single TaskDecorator bean to the auto-configured executor that
    // runs @Async methods.
    @Bean
    public TaskDecorator contextCopyingTaskDecorator() {
        return new ContextCopyingTaskDecorator();
    }
}
