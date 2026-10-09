# NewsPilot - 每日 AI 新闻助手 Agent

一个基于 **LangChain4j + Spring Boot** 的 AI 新闻简报自动生成与推送系统。每天定时为用户整理一份个性化 AI 新闻简报，并通过邮件推送。

## 核心特性

- **Agent 自主决策**：LLM 自主决定调用哪些工具、调用几次、何时停止，非硬编码线性流程
- **双模式 Agent 循环**：AiServices 全自动 function calling + 手写 ReAct 兜底
- **7 个核心工具**：5 个基础工具 + 2 个扩展工具（tavily_search / semantic_search）
- **双路检索**：PgVector 语义检索 + 关键词检索，避免内容重复
- **定时推送**：每天 08:00 自动生成简报并邮件推送（可配置为每分钟测试）
- **用户体系**：支持多用户，每人独立订阅偏好（话题、关键词）
- **前端页面**：Thymeleaf 三页面（填身份 / 设偏好 / 看历史简报）
- **Markdown 渲染**：简报内容以 Markdown 生成，详情页和邮件均渲染为 HTML

## 技术栈

| 层次 | 技术 |
|---|---|
| 主框架 | Spring Boot 3.3.4 |
| Agent 编排 | LangChain4j 0.36.2 |
| LLM | DeepSeek-V3（OpenAI 兼容协议） |
| 新闻搜索 | Tavily API |
| 向量存储 | PostgreSQL + PgVector |
| 定时任务 | Spring `@Scheduled` |
| 邮件推送 | Spring Boot Mail |
| 前端 | Thymeleaf + 原生 HTML/CSS |
| Markdown 渲染 | commonmark-java |

## 项目结构

```
newspilot/
├── src/main/java/com/newspilot/
│   ├── NewsPilotApplication.java          # 启动入口
│   ├── config/
│   │   └── AgentConfig.java               # DeepSeek + PgVector + 工具装配
│   ├── agent/
│   │   ├── NewsAgent.java                 # AiServices 接口（姿势 A：全自动）
│   │   ├── ReActLoopRunner.java           # 手写 ReAct 循环（姿势 B：兜底）
│   │   └── NewsAgentRunner.java           # 编排：A 失败自动转 B
│   ├── tools/
│   │   ├── FileTools.java                 # list_dir / read_file / write_file / search_content
│   │   ├── BashTools.java                 # bash + tavily_search
│   │   └── VectorSearchTools.java         # semantic_search（PgVector）
│   ├── entity/
│   │   ├── AppUser.java                   # 用户实体
│   │   ├── Subscription.java              # 订阅偏好实体
│   │   └── Briefing.java                  # 简报实体
│   ├── repository/
│   │   ├── AppUserRepository.java
│   │   ├── SubscriptionRepository.java
│   │   └── BriefingRepository.java
│   ├── service/
│   │   ├── MarkdownService.java           # Markdown → HTML 渲染
│   │   └── EmailPushService.java          # 邮件推送（HTML 格式）
│   ├── job/
│   │   └── DailyBriefingJob.java          # 定时任务（每天 08:00）
│   └── controller/
│       └── PageController.java            # 页面路由
├── src/main/resources/
│   ├── application.yml                    # 配置文件
│   ├── schema.sql                         # 建表 SQL（启动自动执行）
│   └── templates/                         # Thymeleaf 页面
│       ├── index.html                     # 首页：填身份
│       ├── preferences.html               # 偏好设置
│       ├── briefings.html                 # 历史简报列表
│       └── briefing-detail.html           # 简报详情
└── pom.xml
```

## 快速开始

### 1. 环境要求

- JDK 17+
- Maven 3.8+
- PostgreSQL 14+（带 pgvector 扩展）
- Redis（可选，当前版本未使用）

### 2. 数据库准备

```sql
CREATE DATABASE newspilot;
\c newspilot
CREATE EXTENSION IF NOT EXISTS vector;
```

### 3. 配置环境变量

```powershell
# Windows PowerShell
$env:DEEPSEEK_API_KEY="sk-你的密钥"
$env:TAVILY_API_KEY="tvly-你的密钥"
```

### 4. 修改配置

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  mail:
    username: your-email@qq.com        # SMTP 发件邮箱
    password: your-smtp-auth-code       # SMTP 授权码（非登录密码）
newspilot:
  push:
    to-email: your-email@qq.com         # 默认收件邮箱（无用户时使用）
```

### 5. 启动

```powershell
cd NewsPilot
mvn clean package -DskipTests
java -jar target\newspilot-0.0.1-SNAPSHOT.jar
```

### 6. 访问

| 页面 | URL |
|---|---|
| 首页（填身份） | http://localhost:8080/ |
| 订阅偏好 | http://localhost:8080/preferences |
| 历史简报 | http://localhost:8080/briefings |

## 核心设计

### Agent 循环：LLM 自主决策

**姿势 A：AiServices 全自动 function calling**

```java
NewsAgent agent = AiServices.builder(NewsAgent.class)
    .chatLanguageModel(model)
    .tools(fileTools, bashTools, vectorSearchTools)
    .build();
```

LLM 在对话中自主决定调哪个工具、调几次、何时停。

**姿势 B：手写 ReAct 兜底**

当姿势 A 失败时，自动降级到手写 Thought→Action→Observation 循环，最多 8 步。

### 7 个工具

#### 基础工具（5 个）

| 工具 | 作用 | 应用场景 |
|---|---|---|
| `list_dir(path)` | 列出目录内容 | 检查 `d:/newspilot-briefings/` 已有简报，避免重复 |
| `read_file(path)` | 读取文件 | 读取抓取的新闻原文、历史简报 |
| `search_content(keyword, dir)` | 按关键词搜索目录下文件 | 在简报目录搜特定话题，检查是否已覆盖 |
| `write_file(path, content)` | 写入文件 | 把整理好的简报 Markdown 写到磁盘 |
| `bash(command)` | 执行 shell 命令（含危险命令拦截） | `curl` 抓网页、`grep` 检索、调搜索 API |

#### 扩展工具（2 个）

| 工具 | 作用 | 应用场景 |
|---|---|---|
| `tavily_search(query)` | 调 Tavily API 搜索 | 让模型自主决定搜什么关键词、搜几次，获取最新 AI 新闻 |
| `semantic_search(query)` | PgVector 语义检索 | 用向量相似度找历史简报片段，避免与往期内容重复 |

### 数据流

```
定时任务触发
    ↓
加载用户订阅偏好（subscription 表）
    ↓
Agent 生成简报（LLM 自主调工具）
    ↓
简报写入磁盘（d:/newspilot-briefings/）
    ↓
简报入库（briefing 表）
    ↓
邮件推送到用户邮箱（HTML 格式）
```

## 安全设计

- **bash 工具**：拦截 `rm -rf`、`shutdown`、`reboot`、`format` 等危险命令
- **API 密钥**：DeepSeek/Tavily key 走环境变量，不硬编码
- **邮件密码**：SMTP 授权码配置在 `application.yml`，不入库

## 许可证

MIT
