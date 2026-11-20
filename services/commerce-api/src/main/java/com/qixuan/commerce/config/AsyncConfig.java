package com.qixuan.commerce.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 流式对话专用的线程池。
 *
 * 为什么不用 Spring 默认的 SimpleAsyncTaskExecutor：
 * 它每个任务新建一条线程，一次 30 秒的对话就占一条，并发一上来线程数失控，
 * 也没有队列和拒绝策略。SSE 是长连接 + 长任务，必须用有界的线程池。
 */
@Configuration
public class AsyncConfig {

    @Bean
    public ThreadPoolTaskExecutor streamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("ai-stream-");

        // 队列满时直接拒绝，调用方立刻知道"服务过载"；
        // 无限堆积的后果是所有请求一起超时，反而看不出问题在哪
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
