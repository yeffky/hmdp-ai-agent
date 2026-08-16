package com.hmdp.agent.tool;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.service.IBlogService;
import com.hmdp.agent.tool.param.ToolParamException;
import com.hmdp.utils.IdObfuscator;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 探店笔记工具 — 查询店铺/用户相关笔记。
 */
@Component
public class BlogTool {

    @Resource
    private IBlogService blogService;

    @Resource
    private IdObfuscator idObfuscator;

    @Tool("查询指定店铺的相关探店笔记。需提供店铺ID。返回笔记列表（标题、首图、内容、点赞等）。")
    public Object queryShopBlogs(
            @P("店铺ID（对外混淆ID）") String shopId,
            @P("页码，默认1") Integer current) {
        Long realShopId = idObfuscator.decodeOrId(shopId);
        if (realShopId == null) {
            throw new ToolParamException("需要提供店铺ID才能查询相关笔记");
        }
        return blogService.queryBlogByShopId(realShopId, current == null ? 1 : current).getData();
    }

    @Tool("查询指定用户发布的探店笔记。用户ID可不传，默认当前登录用户。返回笔记列表（标题、首图、内容、点赞等）。")
    public Object queryUserBlogs(
            @P("用户ID（可不传，默认当前登录用户）") Long userId,
            @P("页码，默认1") Integer current) {
        // Agent 执行时 ToolContext 已注入当前用户：不传 userId 默认查自己，避免 agent 反问用户要 ID
        Long uid = userId != null ? userId : com.hmdp.agent.ToolContext.getUserId();
        if (uid == null) {
            throw new ToolParamException("需要提供用户ID才能查询其笔记");
        }
        return blogService.query()
                .eq("user_id", uid)
                .orderByDesc("create_time")
                .page(new Page<>(current == null ? 1 : current, 10))
                .getRecords();
    }
}
