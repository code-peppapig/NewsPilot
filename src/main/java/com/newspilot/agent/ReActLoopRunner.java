package com.newspilot.agent;

import com.newspilot.tools.BashTools;
import com.newspilot.tools.FileTools;
import com.newspilot.tools.VectorSearchTools;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 姿势 B：手写 ReAct 循环兜底。
 *
 * <p>Thought → Action → Observation → ... → Final Answer 的显式循环。
 * 与姿势 A（AiServices 全自动 function calling）相比：
 *   - 优点：每一步可见、可干预、可调试；不依赖 LLM 原生 function calling 能力
 *   - 缺点：要自己解析 LLM 输出，容错性要求高
 *
 * <p>调用方策略：先尝试姿势 A（更优雅、更贴题目意图），失败/超时再落到这里。
 */
@Component
public class ReActLoopRunner {

    private static final int MAX_STEPS = 8;

    private static final Pattern ACTION =
            Pattern.compile("(?is)Action:\\s*(\\w+)\\s*\\n\\s*Input:\\s*(.*)");

    private final ChatLanguageModel chatLanguageModel;
    private final FileTools fileTools;
    private final BashTools bashTools;
    private final VectorSearchTools vectorTools;

    public ReActLoopRunner(ChatLanguageModel chatLanguageModel,
                           FileTools fileTools,
                           BashTools bashTools,
                           VectorSearchTools vectorTools) {
        this.chatLanguageModel = chatLanguageModel;
        this.fileTools = fileTools;
        this.bashTools = bashTools;
        this.vectorTools = vectorTools;
    }

    public String run(String userPreference) {
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(SystemMessage.from("""
                你是一个使用 ReAct 范式的 AI 新闻编辑。
                每一步你必须严格按以下格式输出：

                  Thought: 你的思考
                  Action: 工具名
                  Input: 工具参数（单行字符串）

                可用工具：list_dir / read_file / write_file / search_content / bash / tavily_search / semantic_search
                  - write_file 的 Input 格式：第一行是路径，剩余行是内容（用 \\n 分隔）
                  - search_content 的 Input 格式：关键词 空格 目录路径

                当你认为已经可以给出最终简报时，输出：

                  Thought: ...
                  Final Answer: <Markdown 格式的简报>
                """));
        msgs.add(UserMessage.from(
                "用户订阅偏好：" + userPreference + "\n请整理今日 AI 新闻简报，并写入工作目录。"));

        for (int step = 0; step < MAX_STEPS; step++) {
            AiMessage resp = chatLanguageModel.generate(msgs).content();
            String text = resp.text();
            msgs.add(resp);

            if (text == null || text.contains("Final Answer")) {
                if (text == null) return "ABORT: empty response";
                int i = text.indexOf("Final Answer:");
                return text.substring(i + "Final Answer:".length()).trim();
            }

            Matcher m = ACTION.matcher(text);
            if (!m.find()) {
                return "ABORT: no parseable action\n" + text;
            }

            String tool = m.group(1).trim();
            String input = m.group(2).trim();
            String observation = dispatch(tool, input);
            // 截断超长 observation，保护上下文
            if (observation.length() > 3000) {
                observation = observation.substring(0, 3000) + "...[truncated]";
            }
            msgs.add(UserMessage.from("Observation: " + observation));
        }
        return "ABORT: max steps reached";
    }

    private String dispatch(String tool, String input) {
        try {
            return switch (tool) {
                case "list_dir"        -> String.valueOf(fileTools.list_dir(input));
                case "read_file"       -> fileTools.read_file(input);
                case "write_file"      -> {
                    String[] a = input.split("\\n", 2);
                    yield fileTools.write_file(a[0], a.length > 1 ? a[1] : "");
                }
                case "search_content" -> {
                    String[] a = input.split("\\s+", 2);
                    if (a.length < 2) yield "ERROR: need keyword and dir";
                    yield fileTools.search_content(a[0], a[1]);
                }
                case "bash"            -> bashTools.bash(input);
                case "tavily_search"   -> bashTools.tavily_search(input);
                case "semantic_search" -> vectorTools.semantic_search(input);
                default -> "UNKNOWN TOOL: " + tool;
            };
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
