package com.newspilot;

import com.newspilot.agent.NewsAgentRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.LocalDate;

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

    /**
     * 冒烟测试入口：启动后立即跑一次 Agent，验证 LLM + 工具链能端到端打通。
     * 验证通过后可以注释掉这个 Bean。
     */
    @Bean
    public CommandLineRunner smokeTest(NewsAgentRunner runner,
                                       @Value("${newspilot.briefing.workspace}") String workspace) {
        return args -> {
            String today = LocalDate.now().toString();
            String pref = """
                    身份：Java 后端工程师；关注：LangChain、RAG、Agent；关键词：DeepSeek、PgVector

                    今天的日期：%s
                    简报必须写到这个绝对路径：%s/AI新闻简报-%s.md
                    """.formatted(today, workspace, today);
            System.out.println("\n===== Agent 开始 =====");
            String result = runner.run(pref);
            System.out.println(result);
            System.out.println("===== Agent 结束 =====\n");
        };
    }
}

