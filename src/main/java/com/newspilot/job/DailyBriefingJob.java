package com.newspilot.job;

import com.newspilot.agent.NewsAgentRunner;
import com.newspilot.entity.Briefing;
import com.newspilot.repository.BriefingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 每日简报定时任务：每天 08:00 自动触发 Agent 生成当日 AI 新闻简报。
 *
 * <p>当前为单实例调度（@Scheduled），后续接入 ShedLock JDBC 即可防多实例重复执行。
 */
@Component
public class DailyBriefingJob {

    private static final Logger log = LoggerFactory.getLogger(DailyBriefingJob.class);

    private final NewsAgentRunner newsAgentRunner;
    private final BriefingRepository briefingRepository;

    @Value("${newspilot.briefing.workspace}")
    private String workspace;

    public DailyBriefingJob(NewsAgentRunner newsAgentRunner,
                            BriefingRepository briefingRepository) {
        this.newsAgentRunner = newsAgentRunner;
        this.briefingRepository = briefingRepository;
    }

    /**
     * 每天 08:00:00 执行（服务器本地时间）。
     * cron 表达式：秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 8 * * ?")
    public void generateDailyBriefing() {
        log.info("===== DailyBriefingJob 开始 =====");

        // TODO 后续从 subscription 表加载真实用户偏好；当前先用默认偏好
        String userPreference = """
                身份：Java 后端工程师；关注：LangChain、RAG、Agent；关键词：DeepSeek、PgVector

                今天的日期：%s
                简报必须写到这个绝对路径：%s/AI新闻简报-%s.md
                """.formatted(LocalDate.now(), workspace, LocalDate.now());

        try {
            String content = newsAgentRunner.run(userPreference);
            saveBriefing(content);
            log.info("===== DailyBriefingJob 完成 =====");
        } catch (Exception e) {
            log.error("===== DailyBriefingJob 失败 =====", e);
        }
    }

    private void saveBriefing(String content) {
        LocalDate today = LocalDate.now();
        String filePath = workspace + "/AI新闻简报-" + today + ".md";

        Briefing briefing = new Briefing();
        // userId 暂存 null，后续接入真实用户体系后填充
        briefing.setUserId(null);
        briefing.setContent(content);
        briefing.setFilePath(filePath);
        briefing.setBriefingDate(today);

        briefingRepository.save(briefing);
        log.info("简报已入库：date={} path={}", today, filePath);
    }
}
