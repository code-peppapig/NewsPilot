package com.newspilot.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Shell 与外部搜索工具集。
 *
 * <p>这是这个 Agent 的"新闻来源自由度"所在：模型用 bash 可以 curl 任何网页，
 * 用 tavily_search 调用 Tavily（专为 LLM 设计的搜索 API）。新闻从哪来完全由模型决定。
 *
 * <p>安全：bash 工具做黑名单过滤，禁止 rm -rf / shutdown / reboot 等危险命令。
 */
@Component
public class BashTools {

    @Value("${newspilot.tavily.api-key}")
    private String tavilyKey;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Tool("执行 shell 命令并返回输出。可用于 curl 抓取网页、grep 检索、调外部搜索 API 等。"
          + "禁止使用 rm -rf / shutdown / reboot / format 等危险命令。")
    public String bash(@P("要执行的 shell 命令") String command) {
        // 黑名单：危险命令直接拦截
        if (command.matches("(?i).*(rm\\s+-rf\\s+/|shutdown|reboot|format\\s+[a-z]:).*")) {
            return "BLOCKED: dangerous command";
        }
        try {
            // Windows 上用 cmd /c；若部署到 Linux 改成 bash -c
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", command);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean done = p.waitFor(30, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                return "ERROR: timeout";
            }
            // 截断超长输出，避免上下文爆炸
            return out.length() > 4000 ? out.substring(0, 4000) + "...[truncated]" : out;
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    @Tool("调用 Tavily 搜索 API（专为 LLM 设计），返回与查询相关的网页摘要")
    public String tavily_search(@P("搜索关键词") String query) {
        try {
            Map<String, Object> body = Map.of(
                    "query", query,
                    "max_results", 5,
                    "search_depth", "advanced");
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.tavily.com/search"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + tavilyKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = json.readTree(resp.body());
            StringBuilder sb = new StringBuilder();
            for (JsonNode r : root.path("results")) {
                sb.append("## ").append(r.path("title").asText()).append("\n")
                  .append("URL: ").append(r.path("url").asText()).append("\n")
                  .append("摘要: ").append(r.path("content").asText()).append("\n\n");
            }
            return sb.length() == 0 ? "NO RESULTS" : sb.toString();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
