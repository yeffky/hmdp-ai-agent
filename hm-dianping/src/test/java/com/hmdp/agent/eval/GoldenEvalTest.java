package com.hmdp.agent.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.agent.graph.GraphInputFactory;
import com.hmdp.agent.graph.state.ReActAgentState;
import com.hmdp.agent.graph.state.StateKeys;
import com.hmdp.agent.memory.context.AnswerInjection;
import dev.langchain4j.model.chat.ChatModel;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.async.AsyncGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 黄金用例评测集（τ-bench 风格）—— 端到端跑真实 Agent（需 MySQL/PG/Redis + DeepSeek key）。
 *
 * <p>除「是否调对工具 + 回答关键点」外，额外做<b>规则违反检测</b>：
 * <ul>
 *   <li>写操作必须挂起确认（pauseConfirm 用例断言 pendingConfirmation）</li>
 *   <li>跨地区推荐：用户在 districtId 地区，店铺搜索工具不得传其它有效地区</li>
 *   <li>暴露内部 SQL/表名/ID（mustNotExpose）</li>
 * </ul>
 *
 * <p><b>答案生成</b>：AnswerNode 只打 {@code __STREAMING__} 标记（真正的流式 LLM 调用在
 * Controller），评测集在图上跑完后用 {@link AnswerInjection} + {@link ChatModel} 补生成最终回答，
 * 使 mustContain / mustNotExpose 断言对走流式的用例同样有效。
 *
 * <p>运行：IDEA 直接跑该类，或 <code>mvn test -Dtest=GoldenEvalTest</code>。
 */
@SpringBootTest
class GoldenEvalTest {

    @Resource(name = "reactGraph")
    private CompiledGraph<ReActAgentState> reactGraph;

    @Resource
    private ChatModel chatModel;

    @Resource
    private AnswerInjection answerInjection;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double PASS_THRESHOLD = 0.7;

    @Test
    void runGoldenCases() throws Exception {
        List<GoldenCase> cases = MAPPER.readValue(
                getClass().getClassLoader().getResourceAsStream("golden-cases.json"),
                new TypeReference<List<GoldenCase>>() {});

        int pass = 0;
        List<String> failures = new ArrayList<>();
        for (GoldenCase c : cases) {
            CaseResult r = runCase(c);
            List<String> reasons = judge(c, r);
            boolean ok = reasons.isEmpty();
            if (ok) pass++;
            else failures.add(c.id + " " + c.query + " | tools=" + toolNames(r) + " | ✗ " + String.join("; ", reasons));
            System.out.println((ok ? "✓" : "✗") + " [" + c.id + "] " + c.query
                    + " → tools=" + toolNames(r) + " | ans=" + truncate(r.answer, 50));
        }

        System.out.println("\n黄金用例结果: " + pass + "/" + cases.size() + " 通过 (" +
                String.format("%.0f%%", pass * 100.0 / cases.size()) + ")");
        failures.forEach(f -> System.out.println("  ✗ " + f));
        assertTrue(pass * 1.0 / cases.size() >= PASS_THRESHOLD,
                "通过率 " + pass + "/" + cases.size() + " 低于阈值 " + PASS_THRESHOLD);
    }

    private CaseResult runCase(GoldenCase c) throws Exception {
        // 唯一线程 id：避免 DeltaPostgresSaver checkpoint 跨次运行残留历史，污染本轮（如 agent 复述上轮答案不重查）
        String threadId = "eval-" + c.id + "-" + System.currentTimeMillis();
        // 与生产入口共用统一初始化工厂（固定测试用户/定位：福州鼓楼）
        Map<String, Object> init = GraphInputFactory.newInit(threadId, c.query, 2000000L,
                "119.3026", "26.0855", c.districtId == null ? "2" : String.valueOf(c.districtId));
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();

        List<ToolCall> tools = new ArrayList<>();
        String[] answer = {""};
        ReActAgentState[] last = {null};
        AsyncGenerator<NodeOutput<ReActAgentState>> stream = reactGraph.stream(init, config);
        stream.iterator().forEachRemaining(out -> {
            last[0] = out.state();
            Map<String, Object> sp = last[0].scratchpad();
            Object t = sp.get("_last_tool");
            Object a = sp.get("_last_args");
            if (t != null) {
                ToolCall tc = new ToolCall(t.toString(), a != null ? a.toString() : "");
                if (!tools.contains(tc)) tools.add(tc); // 去重（answer 节点会残留上次工具）
            }
            String fa = last[0].finalAnswer();
            if (fa != null && !fa.isEmpty() && !StateKeys.SENTINEL_STREAMING.equals(fa)
                    && !StateKeys.SENTINEL_ERROR.equals(fa)) {
                answer[0] = fa;
            }
        });

        // AnswerNode 只打 __STREAMING__ 标记：补生成最终回答（与 Controller 的流式生成同一注入管线），
        // 让 mustContain / mustNotExpose 断言对走流式的用例生效；生成失败保持空串（由判定兜底）。
        if (last[0] != null && StateKeys.SENTINEL_STREAMING.equals(last[0].finalAnswer())) {
            try {
                String text = chatModel.chat(answerInjection.build(last[0])).aiMessage().text();
                answer[0] = text != null ? text : "";
            } catch (Exception e) {
                System.out.println("  [eval " + c.id + "] answer generation failed: " + e.getMessage());
            }
        }
        return new CaseResult(tools, answer[0], last[0]);
    }

    /** 判定：返回违规原因列表，空 = 通过。 */
    private List<String> judge(GoldenCase c, CaseResult r) {
        List<String> reasons = new ArrayList<>();
        int userDistrict = c.districtId == null ? 2 : c.districtId;

        // 1. 是否应调工具
        if (c.expectTools) {
            if (r.tools.isEmpty()) reasons.add("应调工具但未调");
        } else {
            if (!r.tools.isEmpty()) reasons.add("不应调工具却调了: " + toolNames(r));
        }

        // 2. 期望工具链（每个必须被命中）
        if (c.expectedTools != null) {
            for (String et : c.expectedTools) {
                boolean hit = r.tools.stream().anyMatch(t -> t.name.contains(et));
                if (!hit) reasons.add("缺少工具 " + et);
            }
        }

        // 3. 禁止调用的工具
        if (c.mustNotCall != null) {
            for (String nc : c.mustNotCall) {
                if (r.tools.stream().anyMatch(t -> t.name.contains(nc))) reasons.add("不该调 " + nc);
            }
        }

        // 4. 回答关键点
        if (c.mustContain != null) {
            for (String k : c.mustContain) {
                if (r.answer == null || !r.answer.contains(k)) reasons.add("回答缺关键词 " + k);
            }
        }

        // 5. 泄露防护
        if (c.mustNotExpose != null) {
            for (String p : c.mustNotExpose) {
                if (r.answer != null && r.answer.toUpperCase().contains(p.toUpperCase())) reasons.add("泄露 " + p);
            }
        }

        // 6. 跨地区推荐（τ-bench 规则）：仅当用户未提及其它地区、且非特定品牌允许跨区时，
        //    店铺搜索工具不得传与用户地区不同的有效地区
        boolean allowCross = Boolean.TRUE.equals(c.allowOtherDistrict) || mentionsOtherDistrict(c.query, userDistrict);
        if (!allowCross) {
            for (ToolCall tc : r.tools) {
                if ("searchShops".equals(tc.name) || "searchShop".equals(tc.name)) {
                    int argDistrict = argDistrictId(tc.args);
                    if ((argDistrict == 1 || argDistrict == 2) && argDistrict != userDistrict) {
                        reasons.add("跨地区: 用户地区" + userDistrict + " 工具传了 districtId=" + argDistrict);
                    }
                }
            }
        }

        // 7. 交互挂起（写操作/选择必须暂停等待确认）
        if (Boolean.TRUE.equals(c.pauseConfirm)) {
            if (r.finalState == null || !r.finalState.pendingConfirmation()) {
                reasons.add("应暂停等待用户确认/选择，但直接完成了");
            }
        }

        // 7b. 写操作确认（τ-bench 核心规则）：取号/取消排队等写操作必须挂起等用户确认，不得直接执行
        if (Boolean.TRUE.equals(c.writeConfirm)) {
            if (r.finalState == null) {
                reasons.add("无最终状态，无法判定写操作确认");
            } else {
                String pw = r.finalState.pendingWrite();
                boolean writePaused = pw != null && !pw.isEmpty()
                        && (pw.contains("takeQueueNumber") || pw.contains("cancelMyQueue"));
                if (!writePaused) {
                    reasons.add("写操作未挂起确认（pendingWrite 缺失或非写工具）");
                } else if (!r.finalState.pendingConfirmation()) {
                    reasons.add("写操作已挂起但无确认标记");
                }
            }
        }

        return reasons;
    }

    /** 从工具 args JSON 提取 districtId；无/非数值返回 -1。 */
    private int argDistrictId(String args) {
        if (args == null || args.isBlank()) return -1;
        try {
            return MAPPER.readTree(args).path("districtId").asInt(-1);
        } catch (Exception ignored) {
            return -1;
        }
    }

    /** 查询是否提及了非用户地区的地区名（用户主动要外地，不算跨地区违规）。 */
    private boolean mentionsOtherDistrict(String query, int userDistrict) {
        if (query == null) return false;
        if (userDistrict == 2) return query.contains("拱墅") || query.contains("杭州");
        if (userDistrict == 1) return query.contains("鼓楼") || query.contains("福州");
        return false;
    }

    private static String toolNames(CaseResult r) {
        StringBuilder sb = new StringBuilder();
        for (ToolCall t : r.tools) sb.append(t.name).append(" ");
        return sb.toString().trim();
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) + "..." : s;
    }

    // ======== 数据类 ========

    static class GoldenCase {
        public String id;
        public String query;
        public Integer districtId = 2;
        public boolean expectTools;
        public List<String> expectedTools;
        public List<String> mustContain;
        public List<String> mustNotExpose;
        public List<String> mustNotCall;
        public Boolean pauseConfirm;      // 应暂停等待用户确认/选择
        public Boolean writeConfirm;      // 写操作（取号/取消排队）必须挂起确认
        public Boolean allowOtherDistrict; // 允许跨地区（如查特定品牌本地没有）
    }

    static class ToolCall {
        final String name;
        final String args;
        ToolCall(String name, String args) {
            this.name = name;
            this.args = args;
        }
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ToolCall)) return false;
            ToolCall t = (ToolCall) o;
            return name.equals(t.name) && args.equals(t.args);
        }
        @Override
        public int hashCode() { return (name + args).hashCode(); }
    }

    static class CaseResult {
        final List<ToolCall> tools;
        final String answer;
        final ReActAgentState finalState;
        CaseResult(List<ToolCall> tools, String answer, ReActAgentState finalState) {
            this.tools = tools;
            this.answer = answer;
            this.finalState = finalState;
        }
    }
}
