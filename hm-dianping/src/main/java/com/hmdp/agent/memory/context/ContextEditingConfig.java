package com.hmdp.agent.memory.context;

import com.hmdp.agent.config.MemoryProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 上下文编辑配置门面 — 从 MemoryProperties 提取注入层（filter/trim）配置，
 * 提供便捷访问。对应 agent.memory.context-editing.*
 */
@Component
public class ContextEditingConfig {

    @Resource
    private MemoryProperties memoryProperties;

    private MemoryProperties.ContextEditing props;

    @PostConstruct
    private void init() {
        this.props = memoryProperties.getContextEditing();
    }

    public boolean isEnabled() {
        return props.isEnabled();
    }

    /** 多条件触发阈值：任一条件满足（条内 tokens/messages AND、条间 OR）即触发裁剪。 */
    public List<MemoryProperties.ContextEditing.Trigger> getTrigger() {
        return props.getTrigger() != null ? props.getTrigger() : new ArrayList<>();
    }

    /** 触发后全量保留最近 N 轮（含工具轨迹，start_on=human 轮次对齐）。 */
    public int getKeepRounds() {
        return props.getKeepRounds();
    }

    /** 白名单工具：即使被裁区域也整条保留其结果。 */
    public List<String> getExcludeTools() {
        return props.getExcludeTools() != null ? props.getExcludeTools() : new ArrayList<>();
    }

    /** 被裁工具结果的替换占位符。 */
    public String getPlaceholder() {
        return props.getPlaceholder() != null ? props.getPlaceholder() : "[已清理]";
    }
}
