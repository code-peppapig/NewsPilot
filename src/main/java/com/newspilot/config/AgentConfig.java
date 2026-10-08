package com.newspilot.config;

import com.newspilot.agent.NewsAgent;
import com.newspilot.tools.BashTools;
import com.newspilot.tools.FileTools;
import com.newspilot.tools.VectorSearchTools;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 核心配置：
 * - ChatLanguageModel：DeepSeek-V3（走 OpenAI 兼容协议）
 * - EmbeddingModel / EmbeddingStore：本地 all-MiniLM-L6-v2 + PgVector 语义检索
 * - NewsAgent：LangChain4j AiServices 装配的"全自动挡" Agent（姿势 A）
 */
@Configuration
public class AgentConfig {

    @Value("${newspilot.llm.base-url}")      private String baseUrl;
    @Value("${newspilot.llm.api-key}")        private String apiKey;
    @Value("${newspilot.llm.model}")          private String model;
    @Value("${newspilot.llm.temperature}")    private double temperature;
    @Value("${spring.datasource.url}")        private String dsUrl;
    @Value("${spring.datasource.username}")   private String dsUser;
    @Value("${spring.datasource.password}")   private String dsPwd;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .temperature(temperature)
                .build();
    }

    /**
     * 本地嵌入模型（all-MiniLM-L6-v2，384 维），避免再依赖外部 embedding API。
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    /**
     * PgVector 向量库：存历史简报/新闻片段，供 semantic_search 工具召回。
     */
    @Bean
    public EmbeddingStore<?> embeddingStore() {
        // 从 jdbc url 里解析出 host:port，简化配置
        String host = "localhost";
        int port = 5432;
        String db = "newspilot";
        return PgVectorEmbeddingStore.builder()
                .host(host)
                .port(port)
                .database(db)
                .user(dsUser)
                .password(dsPwd)
                .table("news_embeddings")
                .dimension(384)   // 与 AllMiniLmL6V2 对齐
                .dropTableFirst(false)
                .build();
    }

    @Bean
    public NewsAgent newsAgent(ChatLanguageModel chatLanguageModel,
                               FileTools fileTools,
                               BashTools bashTools,
                               VectorSearchTools vectorSearchTools) {
        return AiServices.builder(NewsAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .tools(fileTools, bashTools, vectorSearchTools)
                .build();
    }
}
