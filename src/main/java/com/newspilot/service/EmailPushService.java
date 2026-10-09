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
    private final MarkdownService markdownService;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailPushService(JavaMailSender mailSender, MarkdownService markdownService) {
        this.mailSender = mailSender;
        this.markdownService = markdownService;
    }

    /**
     * 发送简报邮件（HTML 格式，Markdown 已渲染）。
     *
     * @param toEmail 收件人邮箱
     * @param subject 邮件主题，如 "AI 新闻简报 - 2026-10-09"
     * @param markdownContent 简报 Markdown 内容
     */
    public void pushBriefing(String toEmail, String subject, String markdownContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            // true = HTML 邮件，Markdown 渲染为 HTML 后发送
            String htmlContent = buildEmailHtml(subject, markdownContent);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("简报邮件已发送：to={} subject={}", toEmail, subject);
        } catch (Exception e) {
            log.error("简报邮件发送失败", e);
        }
    }

    /**
     * 构建邮件 HTML 模板：把 Markdown 渲染结果包一层简单样式。
     */
    private String buildEmailHtml(String subject, String markdownContent) {
        String bodyHtml = markdownService.toHtml(markdownContent);
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; color: #333; line-height: 1.6; max-width: 800px; margin: 0 auto; padding: 20px; }
                    h1 { color: #1a1a2e; font-size: 24px; border-bottom: 2px solid #4a6cf7; padding-bottom: 12px; }
                    h2 { color: #1a1a2e; font-size: 18px; margin-top: 28px; }
                    h3 { color: #333; font-size: 16px; margin-top: 20px; }
                    p { margin: 12px 0; }
                    ul, ol { margin: 12px 0; padding-left: 24px; }
                    li { margin: 6px 0; }
                    blockquote { border-left: 4px solid #4a6cf7; background: #f8f9ff; padding: 12px 16px; margin: 16px 0; color: #555; }
                    code { background: #f0f0f0; padding: 2px 6px; border-radius: 4px; font-family: Consolas, monospace; font-size: 13px; }
                    pre { background: #f0f0f0; padding: 16px; border-radius: 8px; overflow-x: auto; }
                    hr { border: none; border-top: 1px solid #eee; margin: 20px 0; }
                    a { color: #4a6cf7; text-decoration: none; }
                    .header { background: #f8f9ff; padding: 16px; border-radius: 8px; margin-bottom: 24px; }
                    .header h1 { margin: 0; border: none; padding: 0; }
                    .meta { color: #666; font-size: 14px; margin-top: 8px; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>📰 %s</h1>
                    <div class="meta">由 NewsPilot 自动生成并推送</div>
                </div>
                %s
            </body>
            </html>
            """.formatted(subject, bodyHtml);
    }
}
