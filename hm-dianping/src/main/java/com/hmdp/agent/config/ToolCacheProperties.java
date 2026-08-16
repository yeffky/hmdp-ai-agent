package com.hmdp.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具结果缓存配置 — 相同参数的工具结果走 Redis 缓存（避免重复执行外部调用）。
 * 仅对只读、结果稳定、用户无关的工具开启；写操作 / 用户私有 / 易变结果不缓存。
 */
@Component
@ConfigurationProperties(prefix = "agent.tool-cache")
public class ToolCacheProperties {

    /** 是否启用工具结果缓存 */
    private boolean enabled = true;

    /** 缓存有效期（秒），默认 30 分钟 */
    private int ttlSeconds = 1800;

    /** 可缓存工具白名单（只读稳定工具） */
    private List<String> tools = new ArrayList<>(List.of(
            "searchShops", "searchShop", "geoSearch", "recommendShops",
            "listShopVouchers", "queryShopComments", "queryShopBlogs", "queryUserBlogs"));

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getTtlSeconds() { return ttlSeconds; }
    public void setTtlSeconds(int ttlSeconds) { this.ttlSeconds = ttlSeconds; }

    public List<String> getTools() { return tools; }
    public void setTools(List<String> tools) { this.tools = tools; }
}
