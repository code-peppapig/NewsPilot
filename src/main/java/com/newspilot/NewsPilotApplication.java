package com.newspilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 每日 AI 新闻助手 Agent 启动入口。
 * 启用 @EnableScheduling 以便后续挂每日定时任务（DailyBriefingJob）。
 */
@SpringBootApplication
@EnableScheduling
public class NewsPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(NewsPilotApplication.class, args);
    }
}
