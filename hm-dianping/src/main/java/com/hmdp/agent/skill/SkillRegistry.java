package com.hmdp.agent.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * SkillRegistry — 加载 agent-skills/*.skill 目录，按用户意图触发词匹配 skill。
 *
 * skill.md 格式：
 *   ---
 *   name: shop
 *   keywords: 店,火锅,评价,附近,周边
 *   ---
 *   ## 规则
 *   ...
 *   ## schema
 *   ...
 * tools.md（同目录）：每行一个工具名。
 *
 * 匹配不到时回退 general skill（text2sql 全量兜底），保证 C 端长尾查询可用。
 */
@Component
public class SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistry.class);

    private final Map<String, Skill> skills = new LinkedHashMap<>();

    @PostConstruct
    public void init() throws IOException {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:agent-skills/*/SKILL.md");
        for (Resource r : resources) {
            Skill s = parseSkill(resolver, r);
            if (s != null) {
                skills.put(s.name(), s);
            }
        }
        log.info("SkillRegistry loaded {} skills: {}", skills.size(), skills.keySet());
    }

    /** 按用户意图触发词匹配 skill；无命中时才回退 general（text2sql 仅作长尾兜底） */
    public List<Skill> matchSkills(String query) {
        if (query == null || query.isBlank()) {
            return List.of(general());
        }
        List<Skill> matched = new ArrayList<>();
        for (Skill s : skills.values()) {
            if (!"general".equals(s.name()) && matches(s, query)) {
                matched.add(s);
            }
        }
        if (matched.isEmpty()) {
            matched.add(general()); // 确定性 skill 覆盖不到的长尾 → text2sql 兜底
        }
        return matched;
    }

    /** 合并匹配 skill 的工具名集合 */
    public Set<String> toolNames(List<Skill> matched) {
        Set<String> names = new LinkedHashSet<>();
        for (Skill s : matched) {
            names.addAll(s.tools());
        }
        names.add("askUserToChoose"); // 通用交互工具：多选一始终可用（前端渲染选项按钮）
        return names;
    }

    /** 合并匹配 skill 的领域规则文本（注入 agent prompt） */
    public String rulesText(List<Skill> matched) {
        StringBuilder sb = new StringBuilder();
        for (Skill s : matched) {
            if (s.rules() != null && !s.rules().isBlank()) {
                sb.append(s.rules()).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 决策阶段领域规则：SKILL.md 去掉「回答规则」段（那是 Answer 阶段的输出规范），
     * 只留「何时使用/工具决策/流程/护栏」等执行期 SOP——Agent 每轮决策都注入，剔除冗余可显著省 token。
     */
    public String decisionRulesText(List<Skill> matched) {
        StringBuilder sb = new StringBuilder();
        for (Skill s : matched) {
            String rules = s.rules();
            if (rules == null || rules.isBlank()) continue;
            String answerPart = extractAnswerRules(rules);
            String decision = answerPart.isBlank() ? rules : rules.replace(answerPart, "");
            if (decision != null && !decision.isBlank()) {
                sb.append(decision.trim()).append("\n");
            }
        }
        return sb.toString();
    }

    /** 合并匹配 skill 的参考文档文本（L3 资源层：映射表/状态码表等按需加载内容）。
     *  排除 {@code answer-} 前缀文件——那是 Answer 阶段的回答规范（见 {@link #answerRulesText}），
     *  决策阶段注入只会重复占 token。 */
    public String referencesText(List<Skill> matched) {
        if (matched == null || matched.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Skill s : matched) {
            for (Map.Entry<String, String> e : s.references().entrySet()) {
                if (e.getKey().startsWith("answer-")) continue; // Answer 阶段专属
                String content = e.getValue();
                if (content != null && !content.isBlank()) {
                    sb.append("### 参考：").append(e.getKey()).append("\n").append(content).append("\n");
                }
            }
        }
        return sb.toString();
    }

    /**
     * 各 skill 的「回答原则」——Answer 阶段按需加载（不注入工具决策/流程/护栏等 SOP，那是 Agent 决策阶段的事）：
     * <ul>
     *   <li>每个 skill 的 SKILL.md 中「回答规则」段（标题起到下一个二级标题）；</li>
     *   <li>回答阶段 references：文件名以 {@code answer-} 前缀（如 {@code answer-card-examples.md}）。</li>
     * </ul>
     */
    public String answerRulesText(List<Skill> matched) {
        if (matched == null || matched.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Skill s : matched) {
            String answerPart = extractAnswerRules(s.rules());
            if (!answerPart.isBlank()) {
                sb.append(answerPart).append("\n");
            }
        }
        for (Skill s : matched) {
            for (Map.Entry<String, String> e : s.references().entrySet()) {
                if (e.getKey().startsWith("answer-")) {
                    String content = e.getValue();
                    if (content != null && !content.isBlank()) {
                        sb.append("### 参考：").append(e.getKey()).append("\n").append(content).append("\n");
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * 从 SKILL.md 正文提取「回答规则」段：从 {@code ## 回答规则} 标题起到下一个 {@code ## } 二级标题（含）之前。
     * 无该段返回空串（该 skill 无回答原则，Answer 不注入）。
     */
    static String extractAnswerRules(String rules) {
        if (rules == null || rules.isBlank()) return "";
        int i = rules.indexOf("## 回答规则");
        if (i < 0) return "";
        int j = rules.indexOf("## ", i + 4);
        if (j < 0) return rules.substring(i).trim();
        return rules.substring(i, j).trim();
    }

    private boolean matches(Skill s, String query) {
        String desc = s.description();
        if (desc == null || desc.isBlank()) {
            return false;
        }
        // 从 description 的「触发词：」后提取关键词列表匹配（agentskills.io 规范）
        int i = desc.indexOf("触发词：");
        String kwPart = i >= 0 ? desc.substring(i + 4) : desc;
        for (String kw : kwPart.split("[,，]")) {
            if (!kw.isBlank() && query.contains(kw.trim())) {
                return true;
            }
        }
        return false;
    }

    private Skill general() {
        return skills.getOrDefault("general",
                new Skill("general", "", "通用查询兜底", List.of("query")));
    }

    /** 按名取 skill；不存在返回 null */
    public Skill get(String name) {
        return skills.get(name);
    }

    /** 全部 skill（不含 general 兜底） */
    public List<Skill> all() {
        return skills.values().stream()
                .filter(s -> !"general".equals(s.name()))
                .collect(java.util.stream.Collectors.toList());
    }

    private Skill parseSkill(ResourcePatternResolver resolver, Resource r) throws IOException {
        String content = new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String name = "";
        String description = "";
        if (content.startsWith("---")) {
            int end = content.indexOf("---", 3);
            if (end > 0) {
                String fm = content.substring(3, end);
                name = fmVal(fm, "name");
                description = fmVal(fm, "description");
                content = content.substring(end + 3);
            }
        }
        if (name.isEmpty()) {
            return null;
        }
        List<String> tools = new ArrayList<>();
        try {
            Resource toolsRes = resolver.getResource("classpath:agent-skills/" + name + "/tools.md");
            if (toolsRes.exists()) {
                String toolsContent = new String(toolsRes.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                for (String line : toolsContent.split("\n")) {
                    String t = line.trim();
                    if (!t.isEmpty() && !t.startsWith("#")) {
                        tools.add(t);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Skill {} has no tools.md", name);
        }
        String rules = content.trim();
        // L3 资源层：加载 references/*.md（映射表/状态码表等按需文档，SKILL.md 以「读取 references/xxx.md」指令引用）
        Map<String, String> references = new LinkedHashMap<>();
        try {
            Resource[] refRes = resolver.getResources("classpath:agent-skills/" + name + "/references/*.md");
            for (Resource rr : refRes) {
                String fname = rr.getFilename();
                if (fname != null) {
                    references.put(fname, new String(rr.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        } catch (Exception e) {
            log.debug("Skill {} has no references/", name);
        }
        return new Skill(name, description, rules, tools, references);
    }

    private String fmVal(String fm, String key) {
        for (String line : fm.split("\n")) {
            String t = line.trim();
            if (t.startsWith(key + ":")) {
                return t.substring(key.length() + 1).trim();
            }
        }
        return "";
    }
}
