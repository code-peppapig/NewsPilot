package com.newspilot.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 语义检索工具：基于 PgVector 对历史简报做向量召回，避免与往期内容重复。
 * 这是题目要求"语义检索 + 关键字检索双路"中的语义那一路（关键字在 FileTools.search_content）。
 *
 * <p>模型在 ReAct / function calling 流程中会自主决定何时调用本工具——
 * 例如生成简报前先语义检索相似旧稿，避免重复推送相同新闻。
 */
@Component
public class VectorSearchTools {

    private final EmbeddingStore<TextSegment> store;
    private final EmbeddingModel embeddingModel;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public VectorSearchTools(EmbeddingStore store, EmbeddingModel embeddingModel) {
        this.store = store;
        this.embeddingModel = embeddingModel;
    }

    @Tool("基于语义对历史简报做向量检索，返回最相关片段（用于避免与往期简报重复）")
    public String semantic_search(@P("查询文本") String query) {
        try {
            Embedding q = embeddingModel.embed(query).content();
            List<EmbeddingMatch<TextSegment>> matches =
                    store.findRelevant(q, 5, 0.7).matches();
            if (matches.isEmpty()) {
                return "NO MATCH";
            }
            return matches.stream()
                    .map(m -> "score=" + m.score() + " | " + m.embedded().text())
                    .collect(Collectors.joining("\n---\n"));
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
