-- ====================================================================
-- NewsPilot 建表脚本
-- 由 Spring Boot 启动时自动执行（spring.sql.init.mode=always）
-- 所有语句使用 IF NOT EXISTS，保证可重复执行幂等
-- ====================================================================

-- pgvector 扩展：用于 PgVectorEmbeddingStore 的语义检索
CREATE EXTENSION IF NOT EXISTS vector;

-- ====================================================================
-- 用户表：保存用户身份信息（姓名、职业身份、接收简报的邮箱）
-- ====================================================================
CREATE TABLE IF NOT EXISTS app_user (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    identity    VARCHAR(200),
    email       VARCHAR(200) UNIQUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON COLUMN app_user.identity IS '职业身份，如：Java 后端工程师';
COMMENT ON COLUMN app_user.email   IS '接收简报推送的邮箱';

-- ====================================================================
-- 订阅偏好表：每个用户一份，存关注话题、关键词
-- ====================================================================
CREATE TABLE IF NOT EXISTS subscription (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    topics      TEXT,
    keywords    TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON COLUMN subscription.topics   IS '关注话题，逗号分隔，如：LangChain,RAG,Agent';
COMMENT ON COLUMN subscription.keywords IS '关注关键词，逗号分隔，如：DeepSeek,PgVector';

CREATE INDEX IF NOT EXISTS idx_subscription_user ON subscription(user_id);

-- ====================================================================
-- 历史简报表：保存每次 Agent 生成的简报
-- ====================================================================
CREATE TABLE IF NOT EXISTS briefing (
    id             BIGSERIAL     PRIMARY KEY,
    user_id        BIGINT        REFERENCES app_user(id) ON DELETE SET NULL,
    content        TEXT          NOT NULL,
    file_path      VARCHAR(500),
    briefing_date  DATE          NOT NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW()
);
COMMENT ON COLUMN briefing.content        IS '简报 Markdown 全文';
COMMENT ON COLUMN briefing.file_path      IS '简报落盘文件路径（write_file 工具写入）';
COMMENT ON COLUMN briefing.briefing_date  IS '简报对应日期，每天一份';

CREATE INDEX IF NOT EXISTS idx_briefing_user  ON briefing(user_id);
CREATE INDEX IF NOT EXISTS idx_briefing_date ON briefing(briefing_date);

-- ====================================================================
-- news_embeddings 表不用手建：
-- 由 LangChain4j 的 PgVectorEmbeddingStore 在启动时自动创建（dimension=384）
-- 用于 semantic_search 工具对历史简报做语义召回，避免与往期内容重复
-- ====================================================================
