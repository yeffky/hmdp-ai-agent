package com.hmdp.agent.skill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent Skill — 领域知识 + 工具清单。
 * 由 agent-skills 目录下各 skill 的 SKILL.md（frontmatter + 指令正文）、tools.md（工具清单）和
 * references 子目录（按需加载的参考文档，如映射表/状态码表，L3 资源层）解析而来。
 */
public class Skill {

    private final String name;
    private final String description;
    private final String rules;
    private final List<String> tools;
    /** 文件名（不含 references/ 前缀）→ 文件内容；SKILL.md 中通过「读取 references/xxx.md」指令按需触发 */
    private final Map<String, String> references;

    public Skill(String name, String description, String rules, List<String> tools) {
        this(name, description, rules, tools, new LinkedHashMap<>());
    }

    public Skill(String name, String description, String rules, List<String> tools,
                 Map<String, String> references) {
        this.name = name;
        this.description = description;
        this.rules = rules;
        this.tools = tools;
        this.references = references;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String rules() {
        return rules;
    }

    public List<String> tools() {
        return tools;
    }

    /** 有序的参考文档：文件名 → 内容（保留加载顺序） */
    public Map<String, String> references() {
        return references;
    }

    /** 是否存在参考文档 */
    public boolean hasReferences() {
        return references != null && !references.isEmpty();
    }
}
