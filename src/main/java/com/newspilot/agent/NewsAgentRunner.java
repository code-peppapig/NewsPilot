package com.newspilot.agent;

import org.springframework.stereotype.Component;

/**
 * Agent 编排入口：先跑姿势 A（AiServices 全自动 function calling），
 * 失败再落到姿势 B（手写 ReAct 兜底）。
 *
 * <p>这样既满足题目核心要求①"工具调用顺序由 LLM 决定"（A 直接交给 LLM），
 * 又能在 LLM 不稳定时保留可见的 ReAct 调试通道（B）。
 */
@Component
public class NewsAgentRunner {

    private final NewsAgent autoAgent;
    private final ReActLoopRunner reactRunner;

    public NewsAgentRunner(NewsAgent autoAgent, ReActLoopRunner reactRunner) {
        this.autoAgent = autoAgent;
        this.reactRunner = reactRunner;
    }

    public String run(String userPreference) {
        try {
            return autoAgent.generateBriefing(userPreference);
        } catch (Exception e) {
            // 全自动挡失败 → 落到手写 ReAct 兜底
            return "[fallback to ReAct]\n" + reactRunner.run(userPreference);
        }
    }
}
