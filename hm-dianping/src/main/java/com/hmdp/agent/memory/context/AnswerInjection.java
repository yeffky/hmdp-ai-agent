package com.hmdp.agent.memory.context;

import com.hmdp.agent.graph.nodes.Transcript;
import com.hmdp.agent.graph.state.ReActAgentState;
import com.hmdp.agent.skill.Skill;
import com.hmdp.agent.skill.SkillRegistry;
import com.hmdp.utils.IdObfuscator;
import com.hmdp.utils.ShopResultIdObfuscator;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Answer 注入器 — 组装最终回答模型的消息列表（对齐 LangChain 组合拳注入形态）。
 *
 * <p>注入形态：{@code [角色, 规则段, 摘要message, 无历史contextBlock, ...最近 N 轮完整轨迹]}。
 * <ul>
 *   <li><b>最近 N 轮显式封装</b>：最近 {@code keep-recent-rounds} 个真实 user 边界之后的完整轨迹
 *       （提问 + assistant tool_calls + tool 结果 + agent 决策文本），由 messages 通道抽取。
 *       只取最后一轮会让 Answer 阶段看不到上一轮的店名/工具结果，指代性确认
 *       （如「是的就是这家」）生成答案时丢失上下文，与 Agent 决策阶段 contextBlock 历史不一致；</li>
 *   <li><b>更早历史只走摘要</b>：N 轮之前的轨迹不注入（切断跨轮污染），由 compressedSummary 浓缩承载；</li>
 *   <li><b>注入日志</b>：打印注入消息数 / 本轮 tool 结果数 / 最后 user 提问，供"注入在哪"可观测。</li>
 * </ul>
 */
@Component
public class AnswerInjection {

    private static final Logger log = LoggerFactory.getLogger(AnswerInjection.class);

    private static final String ROLE = "你是生活优选AI客服小优。友好、专业、简洁。";

    @Resource
    private ContextEditor contextEditor;

    @Resource
    private SkillRegistry skillRegistry;

    @Resource
    private CompressionConfig compressionConfig;

    @Resource
    private IdObfuscator idObfuscator;

    public List<ChatMessage> build(ReActAgentState state) {
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(SystemMessage.from(ROLE));
        // 规则段（AnswerNode GENERATE 组装：Markdown/脱敏/[[id]] 卡片占位符/展示要求）
        String rules = state.streamingPrompt();
        if (rules != null && !rules.isBlank()) msgs.add(SystemMessage.from(rules));
        // 摘要 message（SummarizationMiddleware 等价：compressedSummary 承载更早轮次的浓缩历史）
        String summary = state.compressedSummary();
        if (summary != null && !summary.isBlank()) {
            msgs.add(SystemMessage.from("以下是此前的对话摘要：\n" + summary));
        }
        // 上下文（无历史段：画像/定位/地区/过往经验，历史由摘要承担）
        String ctx = state.contextBlockNoHistory();
        if (ctx != null && !ctx.isBlank()) msgs.add(SystemMessage.from(ctx));
        // skill 全貌（何时使用/工具决策/流程/护栏/回答规则 + references）：指导 Answer 的输入输出格式与流程。
        // 卡片等展示规则由各 skill 的 SKILL.md「回答规则」段统一约束（含硬性措辞与 references 示例），
        // 代码层不做硬编码——展示规则属于领域层，归属 skill（对齐 A2UI/Claude UI elements 的协议化思路）。
        String skillRules = skillRules(state);
        if (skillRules != null && !skillRules.isBlank()) {
            msgs.add(SystemMessage.from(skillRules));
            // 可观测：每次 Answer 注入了哪些 skill 的领域知识（SKILL.md 规则 + references），
            // grep 关键字「Answer: 注入 skill」即可在日志中观察
            log.debug("Answer: 注入 skill 领域知识 {} 字符（skills={}）",
                    skillRules.length(), resolveSkillNames(state));
        }

        // 最近 N 轮显式封装：从第 N 个真实 user（从后往前）之后开始注入完整轨迹。
        // 修复「Answer 阶段与 Agent 决策阶段上下文不一致」：Agent 决策的 system=contextBlock 含最近
        // keepRecentRounds 轮对话历史，而 Answer 若只注入最后一轮，指代（如「是的就是这家」）会丢失来源。
        List<Map<String, String>> messages = new ArrayList<>(state.messages());
        Transcript.filterMessages(messages); // 孤儿清理
        int keepRounds = compressionConfig != null ? compressionConfig.getKeepRecentRounds() : 3;
        int anchor = Transcript.nthRealUserIdx(messages, keepRounds);
        List<Map<String, String>> round;
        if (anchor < 0) {
            round = messages; // 无足够轮次（异常兜底，全量）
        } else {
            round = new ArrayList<>(messages.subList(anchor, messages.size()));
        }
        // Answer 阶段不携带工具定义（无 toolSpecifications）：把 assistant(tool_calls) 删除、
        // tool 结果折叠为纯文本（证据保留）。否则「历史含 tool_calls 但请求无 tools 定义」时，
        // DeepSeek 会把工具调用以 <tool_calls><invoke name="..."> XML 写进 content 整段回给用户（真实事故）。
        // 折叠时对店铺类工具结果的数字 id 做运行时混淆：历史会话注入的旧数据可能还是数据库真实数字 id，
        // 统一换成对外短串，保证 LLM 上下文里永远看不到真实 DB id，且占位符值与卡片收集 key 一致。
        foldToolMessages(round);
        // 注入前 trim 保护（多轮超限时才触发；正常 keepRounds 轮内不裁剪）
        contextEditor.apply(round);
        msgs.addAll(Transcript.rebuildMessages(round));
        // 可观测：把实际发给 LLM 的完整 Answer prompt 记入日志（每条消息全量、不截断），
        // 排查「LLM 输出丢 [[id]] / 回答不对」时 grep「ANSWER PROMPT (完整)」即可看到
        // 规则段 / skill 回答规则 / 最近 N 轮轨迹（含工具结果折叠文本里的 id 值）到底注入了什么。
        logFullPrompt(msgs);
        return msgs;
    }

    /** 完整渲染 Answer 阶段发给 LLM 的消息列表（含 System 规则/skill 规则/历史轨迹/工具结果折叠），全部原文不截断。 */
    private void logFullPrompt(List<ChatMessage> msgs) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("\n========== ANSWER PROMPT (完整) BEGIN ==========\n");
            int idx = 1;
            for (ChatMessage m : msgs) {
                sb.append("----- [").append(idx++).append("] ").append(roleLabel(m)).append(" -----\n");
                String content = messageText(m);
                sb.append(content == null || content.isEmpty() ? "(无文本)" : content).append("\n");
            }
            sb.append("========== ANSWER PROMPT (完整) END ==========");
            log.info("Answer prompt 完整消息列表（{} 条, {} 字符）:{}", msgs.size(), sb.length(), sb);
        } catch (Exception e) {
            log.warn("记录 Answer prompt 失败: {}", e.getMessage());
        }
    }

    private static String roleLabel(ChatMessage m) {
        if (m instanceof SystemMessage) return "System";
        if (m instanceof UserMessage) return "User";
        if (m instanceof AiMessage ai) {
            return ai.hasToolExecutionRequests() ? "Assistant(tool_calls)" : "Assistant";
        }
        if (m instanceof ToolExecutionResultMessage) return "Tool";
        return String.valueOf(m.type());
    }

    /** 各消息子类的文本提取（与 LlmTraceListener 一致；tool_calls 消息返回工具名摘要）。 */
    private static String messageText(ChatMessage m) {
        if (m instanceof AiMessage ai) {
            return ai.hasToolExecutionRequests()
                    ? "(工具调用: " + ai.toolExecutionRequests().stream()
                    .map(r -> r.name() + "(" + r.arguments() + ")").collect(Collectors.joining("; ")) + ")"
                    : (ai.text() != null ? ai.text() : "");
        }
        if (m instanceof UserMessage u) return u.singleText();
        if (m instanceof SystemMessage s) return s.text();
        if (m instanceof ToolExecutionResultMessage t) return t.text();
        return null;
    }

    /**
     * 折叠工具往返消息为纯文本：删除 assistant 的 tool_calls 中间消息，
     * 把 tool 结果改写为 {@code 工具[xxx] 结果：...} 的 user 文本消息，
     * 其中店铺类工具结果的数字 id 统一混淆为对外短串（见 {@link ShopResultIdObfuscator}）。
     * 这样 Answer 阶段的消息列表不含任何 tool_calls/tool 结构（与无 tools 定义的请求一致），
     * 模型不会被诱导输出 XML 工具调用，同时工具结果作为证据仍保留在上下文中。
     */
    private void foldToolMessages(List<Map<String, String>> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, String> m = messages.get(i);
            if ("assistant".equals(m.get("role"))
                    && m.get("toolCalls") != null && !m.get("toolCalls").isBlank()) {
                messages.remove(i);
            } else if ("tool".equals(m.get("role"))) {
                String toolName = m.getOrDefault("toolName", "");
                String content = m.getOrDefault("content", "");
                // 历史旧数据的数字 id → 对外混淆短串（新数据本就是短串，原样保留）；
                // 同时精简店铺结果为 LLM 回答所需的最小字段集（去 image 长 URL 等，压缩 ~70%）
                String compacted = ShopResultIdObfuscator.compact(toolName, content, idObfuscator);
                Map<String, String> folded = new LinkedHashMap<>();
                folded.put("role", "user");
                folded.put("content", "工具[" + toolName + "] 结果：" + compacted);
                messages.set(i, folded);
            }
        }
    }

    /** 详细描述注入的消息列表（role + 内容概要），供"注入在哪"可观测。 */
    private static String describe(List<ChatMessage> msgs) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : msgs) {
            String label;
            String content;
            if (m instanceof SystemMessage) {
                label = "System";
                content = ((SystemMessage) m).text();
            } else if (m instanceof UserMessage) {
                label = "User";
                content = ((UserMessage) m).singleText();
            } else if (m instanceof AiMessage ai) {
                if (ai.hasToolExecutionRequests()) {
                    label = "Assistant(tool_calls)";
                    content = ai.toolExecutionRequests().stream()
                            .map(r -> r.name()).collect(Collectors.joining(", "));
                } else {
                    label = "Assistant";
                    content = ai.text() != null ? ai.text() : "";
                }
            } else if (m instanceof ToolExecutionResultMessage t) {
                label = "Tool(" + t.toolName() + ")";
                content = t.text();
            } else {
                label = String.valueOf(m.type());
                content = m.toString();
            }
            sb.append("  [").append(label).append("] ").append(truncate(content, 160)).append("\n");
        }
        return sb.toString().trim();
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) + "..." : s;
    }

    /** 匹配 skill 的回答原则（Answer 阶段按需加载）：各 skill 的「回答规则」段 + answer-* references，
     *  不注入工具决策/流程/护栏等 SOP（那是 Agent 决策阶段的领域知识，见 ContextNode#buildDomainRules）。 */
    private String skillRules(ReActAgentState state) {
        List<Skill> matched = resolveSkills(state);
        if (matched == null || matched.isEmpty()) return "";
        return skillRegistry.answerRulesText(matched);
    }

    /** 本次注入领域知识的 skill 名列表（日志观测用）。 */
    private String resolveSkillNames(ReActAgentState state) {
        List<Skill> matched = resolveSkills(state);
        if (matched == null || matched.isEmpty()) return "(无)";
        return matched.stream().map(Skill::name).collect(Collectors.joining(","));
    }

    /** skill 匹配：优先 Planner 语义选中的 selectedSkills，为空回退触发词匹配。 */
    private List<Skill> resolveSkills(ReActAgentState state) {
        List<String> selected = state.selectedSkills();
        if (selected != null && !selected.isEmpty()) {
            List<Skill> skills = new ArrayList<>();
            for (String name : selected) {
                Skill s = skillRegistry.get(name);
                if (s != null) skills.add(s);
            }
            if (!skills.isEmpty()) return skills;
        }
        return skillRegistry.matchSkills(state.userQuery());
    }
}
