package com.newspilot.job;

import com.newspilot.agent.NewsAgentRunner;
import com.newspilot.entity.AppUser;
import com.newspilot.entity.Briefing;
import com.newspilot.entity.Subscription;
import com.newspilot.repository.AppUserRepository;
import com.newspilot.repository.BriefingRepository;
import com.newspilot.repository.SubscriptionRepository;
import com.newspilot.service.EmailPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 每日简报定时任务：每天 08:00 自动触发 Agent 生成当日 AI 新闻简报。
 *
 * <p>从 subscription 表加载真实用户偏好，为每个用户生成个性化简报。
 */
@Component
public class DailyBriefingJob {

    private static final Logger log = LoggerFactory.getLogger(DailyBriefingJob.class);

    private final NewsAgentRunner newsAgentRunner;
    private final BriefingRepository briefingRepository;
    private final EmailPushService emailPushService;
    private final AppUserRepository appUserRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Value("${newspilot.briefing.workspace}")
    private String workspace;

    public DailyBriefingJob(NewsAgentRunner newsAgentRunner,
                            BriefingRepository briefingRepository,
                            EmailPushService emailPushService,
                            AppUserRepository appUserRepository,
                            SubscriptionRepository subscriptionRepository) {
        this.newsAgentRunner = newsAgentRunner;
        this.briefingRepository = briefingRepository;
        this.emailPushService = emailPushService;
        this.appUserRepository = appUserRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    /**
     * 每天 08:00:00 执行（服务器本地时间）。
     * cron 表达式：秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 8 * * ?")
    public void generateDailyBriefing() {
        log.info("===== DailyBriefingJob 开始 =====");

        // 从 subscription 表加载真实用户偏好
        List<Subscription> subscriptions = subscriptionRepository.findAll();
        if (subscriptions.isEmpty()) {
            log.warn("没有用户订阅，使用默认偏好生成简报");
            runWithDefaultPreference();
            return;
        }

        for (Subscription sub : subscriptions) {
            try {
                AppUser user = appUserRepository.findById(sub.getUserId()).orElse(null);
                if (user == null) {
                    log.warn("订阅对应的用户不存在，跳过：userId={}", sub.getUserId());
                    continue;
                }
                runForUser(user, sub);
            } catch (Exception e) {
                log.error("为用户生成简报失败：userId={}", sub.getUserId(), e);
            }
        }

        log.info("===== DailyBriefingJob 完成 =====");
    }

    private void runWithDefaultPreference() {
        String today = LocalDate.now().toString();
        String userPreference = """
                身份：Java 后端工程师；关注：LangChain、RAG、Agent；关键词：DeepSeek、PgVector

                今天的日期：%s
                简报必须写到这个绝对路径：%s/AI新闻简报-%s.md
                """.formatted(today, workspace, today);

        try {
            String content = newsAgentRunner.run(userPreference);
            saveBriefing(content, null);
            pushBriefingEmail(content);
            log.info("===== DailyBriefingJob 完成 =====");
        } catch (Exception e) {
            log.error("===== DailyBriefingJob 失败 =====", e);
        }
    }

    private void runForUser(AppUser user, Subscription sub) {
        String today = LocalDate.now().toString();
        String userPreference = """
                身份：%s；关注：%s；关键词：%s

                今天的日期：%s
                简报必须写到这个绝对路径：%s/AI新闻简报-%s.md
                """.formatted(user.getIdentity(), sub.getTopics(), sub.getKeywords(), today, workspace, today);

        String content = newsAgentRunner.run(userPreference);
        saveBriefing(content, user.getId());
        pushBriefingEmail(content);
    }

    private void saveBriefing(String content, Long userId) {
        LocalDate today = LocalDate.now();
        String filePath = workspace + "/AI新闻简报-" + today + ".md";

        Briefing briefing = new Briefing();
        briefing.setUserId(userId);
        briefing.setContent(content);
        briefing.setFilePath(filePath);
        briefing.setBriefingDate(today);

        briefingRepository.save(briefing);
        log.info("简报已入库：userId={} date={} path={}", userId, today, filePath);
    }

    private void pushBriefingEmail(String content) {
        String subject = "AI 新闻简报 - " + LocalDate.now();
        emailPushService.pushBriefing(subject, content);
    }
}
