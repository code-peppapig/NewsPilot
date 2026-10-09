package com.newspilot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

/**
 * 邮件推送服务：把生成的简报发送到指定邮箱。
 */
@Service
public class EmailPushService {

    private static final Logger log = LoggerFactory.getLogger(EmailPushService.class);

    private final JavaMailSender mailSender;

    @Value("${newspilot.push.to-email}")
    private String toEmail;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailPushService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * 发送简报邮件。
     *
     * @param subject 邮件主题，如 "AI 新闻简报 - 2026-10-09"
     * @param content 简报 Markdown 内容（会作为纯文本发送）
     */
    public void pushBriefing(String subject, String content) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(content, false); // false = 纯文本，不是 HTML

            mailSender.send(message);
            log.info("简报邮件已发送：to={} subject={}", toEmail, subject);
        } catch (Exception e) {
            log.error("简报邮件发送失败", e);
        }
    }
}
