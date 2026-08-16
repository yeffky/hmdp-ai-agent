package com.hmdp.agent.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * showCards —— 声明式 UI 工具（对齐 OpenAI/Anthropic「工具调用驱动 UI」范式）：
 *
 * <p>回答涉及店铺（推荐/查询/列举/比较/单店详情）时，Agent 在输出最终回答前调用本工具，
 * 声明要在回答中展示的店铺卡片（shopIds = 工具结果里这些店的 id，对外混淆短串）。
 * 系统拦截本工具调用（不真正执行），把 shopIds 记入状态，Answer 流式回答完成后
 * 按这些 id 从工具结果提取卡片、按店名 anchor 定位插入回答文本。
 *
 * <p>相比「LLM 在文本里输出 [[id]] 占位符」：工具调用的 JSON 参数由模型结构化生成、
 * schema 校验兜底，可靠性远高于自由文本标记跟随度；文本与 UI 彻底分离——LLM 只写店名+亮点，
 * 卡片由系统渲染（主流开源项目做法，参考 receptron GUI Chat Protocol / OpenAI function calling）。
 */
@Component
public class ShowCardsTool {

    @Tool("声明要在最终回答中展示的店铺卡片。回答涉及店铺（推荐/查询/列举/比较/单店详情）时，"
            + "在输出最终回答前调用本工具，把要展示的店铺 id 放入 shopIds（必须来自 searchShops/searchShop/"
            + "recommendShops/geoSearch 工具结果的 id 字段，禁止编造）。系统会为这些店渲染卡片，"
            + "回答文本只需正常写店名 + 一句亮点，不要输出任何标记、编号或 id。")
    public String showCards(
            @P("店铺 ID 列表（来自店铺工具结果的 id 字段，对外混淆短串）") List<String> shopIds) {
        // 声明式工具：由 AgentNode 拦截记录（不执行、不挂起），此方法不应真正执行
        return "已记录 " + (shopIds == null ? 0 : shopIds.size()) + " 张店铺卡片声明";
    }
}
