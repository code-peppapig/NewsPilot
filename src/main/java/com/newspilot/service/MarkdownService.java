package com.newspilot.service;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Service;

/**
 * Markdown 渲染服务：把 Agent 生成的 Markdown 简报转换为 HTML。
 */
@Service
public class MarkdownService {

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().build();

    /**
     * 将 Markdown 文本转换为 HTML。
     *
     * @param markdown 原始 Markdown 文本
     * @return 渲染后的 HTML 字符串
     */
    public String toHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "<p>暂无内容</p>";
        }
        Node document = parser.parse(markdown);
        return renderer.render(document);
    }
}
