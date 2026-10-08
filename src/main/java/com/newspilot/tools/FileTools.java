package com.newspilot.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件类工具集：list_dir / read_file / write_file / search_content。
 * 这是题目硬性要求的 5 个工具中前 4 个。
 */
@Component
public class FileTools {

    @Tool("列出指定目录下的所有文件和子目录名称")
    public List<String> list_dir(@P("目录绝对路径") String path) {
        try (Stream<Path> s = Files.list(Paths.get(path))) {
            return s.map(p -> p.getFileName().toString())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return List.of("ERROR: " + e.getMessage());
        }
    }

    @Tool("读取指定路径文件的完整文本内容")
    public String read_file(@P("文件绝对路径") String path) {
        try {
            return Files.readString(Paths.get(path));
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    @Tool("写入文本内容到指定路径文件（覆盖写入，自动创建父目录）")
    public String write_file(@P("目标文件绝对路径") String path,
                             @P("要写入的文本内容") String content) {
        try {
            Path p = Paths.get(path);
            if (p.getParent() != null) {
                Files.createDirectories(p.getParent());
            }
            Files.writeString(p, content,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            return "OK: written to " + path;
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    @Tool("在指定目录下递归搜索包含关键词的文件，返回匹配的文件路径与片段")
    public String search_content(@P("要搜索的关键词") String keyword,
                                @P("搜索根目录绝对路径") String dir) {
        StringBuilder sb = new StringBuilder();
        try (Stream<Path> walk = Files.walk(Paths.get(dir))) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".txt") || p.toString().endsWith(".md"))
                .forEach(p -> {
                    try {
                        String text = Files.readString(p);
                        int idx = text.indexOf(keyword);
                        if (idx >= 0) {
                            int from = Math.max(0, idx - 40);
                            int to = Math.min(text.length(), idx + keyword.length() + 80);
                            sb.append(p).append(" => ...")
                              .append(text, from, to).append("...\n");
                        }
                    } catch (IOException ignored) {
                        // 忽略单个文件读取失败
                    }
                });
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
        return sb.length() == 0 ? "NO MATCH" : sb.toString();
    }
}
