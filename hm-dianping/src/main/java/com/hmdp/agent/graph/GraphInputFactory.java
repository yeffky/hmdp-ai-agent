package com.hmdp.agent.graph;

import com.hmdp.agent.graph.state.StateKeys;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 图输入初始化工厂 —— 统一各入口（SSE 流式 / 非流式 / 评测集）的初始状态构建。
 *
 * <p>历史问题：{@code ReactStreamController}、{@code ChatRagController}、黄金用例评测集
 * 各自维护一份 init Map，字段不一致导致同一图在不同入口下行为漂移（如非流式入口缺定位/地区）。
 * 所有入口统一走本工厂，保证初始状态形状一致。
 */
public final class GraphInputFactory {

    private GraphInputFactory() {}

    /**
     * 构建一次新对话的完整初始状态。
     *
     * @param sessionId      会话 id（如 "user:123"）
     * @param userQuery      本轮用户请求
     * @param userId         当前登录用户 id（可空）
     * @param centerX        用户定位经度（可空，如 "119.3026"）
     * @param centerY        用户定位纬度（可空，如 "26.0855"）
     * @param districtIdStr  用户当前地区 id 字符串（可空，如 "2"）
     */
    public static Map<String, Object> newInit(String sessionId, String userQuery, Long userId,
                                              String centerX, String centerY, String districtIdStr) {
        Map<String, Object> init = new LinkedHashMap<>();
        init.put(StateKeys.SESSION_ID, sessionId);
        init.put(StateKeys.USER_QUERY, userQuery);
        init.put(StateKeys.USER_LOCATION, buildLocation(centerX, centerY));
        if (districtIdStr != null && !districtIdStr.isBlank()) {
            try {
                init.put(StateKeys.USER_DISTRICT_ID, Long.parseLong(districtIdStr.trim()));
            } catch (NumberFormatException ignored) {
                // 非法地区 id 忽略，由上下文按默认地区兜底
            }
        }
        init.put(StateKeys.COUNTERS, newCounters());
        init.put(StateKeys.ERROR_CATEGORY, "");
        init.put(StateKeys.LAST_TOOL_NAME, "");
        init.put(StateKeys.LAST_TOOL_ARGS, "");
        init.put(StateKeys.PENDING_CONFIRMATION, false);
        init.put(StateKeys.CONFIRMATION_PROMPT, "");
        init.put(StateKeys.USER_CHOICE, "");
        init.put(StateKeys.PENDING_WRITE, "");
        init.put(StateKeys.PENDING_OPTIONS, "");
        init.put(StateKeys.SCRATCHPAD, new LinkedHashMap<>());
        init.put(StateKeys.MESSAGES, new ArrayList<>());
        init.put(StateKeys.COMPRESSED_SUMMARY, "");
        init.put(StateKeys.NEXT_NODE, NodeNames.CONTEXT);
        init.put(StateKeys.FINAL_ANSWER, "");
        init.put(StateKeys.PLAN, "");
        init.put(StateKeys.SELECTED_SKILLS, "");
        init.put(StateKeys.CONTEXT_BLOCK, "");
        init.put(StateKeys.CONTEXT_BLOCK_NO_HISTORY, "");
        init.put(StateKeys.STREAMING_PROMPT, "");
        init.put(StateKeys.ROUND_EVIDENCE, "");
        init.put(StateKeys.ERROR_CONTEXT, "");
        if (userId != null) {
            init.put(StateKeys.USER_ID, userId);
        }
        return init;
    }

    /** 全零轮次计数器 Map。 */
    public static Map<String, Object> newCounters() {
        Map<String, Object> counters = new LinkedHashMap<>();
        counters.put(StateKeys.ITERATION, 0);
        counters.put(StateKeys.RETRY_COUNT, 0);
        counters.put(StateKeys.FATAL_ERROR_COUNT, 0);
        counters.put(StateKeys.EMPTY_RESULT_RETRIES, 0);
        counters.put(StateKeys.REPLAN_COUNT, 0);
        counters.put(StateKeys.TOOL_CALL_COUNT, 0);
        return counters;
    }

    /** 构造用户定位文本：前端地区中心坐标 → "经度X，纬度Y"（geoSearch 直接用）；坐标缺失返回空。 */
    public static String buildLocation(String centerX, String centerY) {
        if (centerX == null || centerY == null || centerX.isBlank() || centerY.isBlank()) {
            return "";
        }
        return "经度" + centerX.trim() + "，纬度" + centerY.trim();
    }
}
