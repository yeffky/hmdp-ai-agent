package com.hmdp.agent.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * askUserToChoose —— 需要用户从多个选项中选择时调用（如列出的多家店/多个套餐）。
 *
 * <p>该工具<b>不真正执行</b>：AgentNode 拦截调用，转成「确认挂起」（pendingOptions），
 * 前端渲染选项按钮，用户点击后以 userChoice 恢复，Agent 拿到选择继续执行。
 * 相比纯文本提问，这种方式让选店/选择走确定性确认流（同写操作确认）。
 */
@Component
public class AskUserToChooseTool {

    @Tool("当需要用户从多个选项中选择时调用（如列出多家店/多个套餐让用户选）。" +
          "**调用前请先在回复文本中写好对各选项的简短介绍**（每家店/每个套餐的亮点，一两句话），" +
          "系统会把这段介绍加工成推荐语展示给用户，并在其后渲染选择按钮；调用后暂停等待用户选择，继续后再执行下一步。")
    public String askUserToChoose(
            @P("给用户看的提示语，如「请选择一家火锅店」") String prompt,
            @P("选项列表（如店名/套餐名），用户从中选择一项") List<String> options) {
        // 由 AgentNode 拦截转成确认挂起，此方法不应真正执行
        return "已请求用户选择";
    }
}
