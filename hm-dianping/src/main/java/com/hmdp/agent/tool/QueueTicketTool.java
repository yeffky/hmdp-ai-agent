package com.hmdp.agent.tool;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IQueueTicketService;
import com.hmdp.utils.IdObfuscator;
import com.hmdp.utils.UserHolder;
import com.hmdp.agent.tool.param.ToolParams;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 排队取号工具 — 供 ReAct Agent 调用。
 * 用户可以通过对话让 Agent 帮其在指定商铺排队取号、查询排队进度、取消排队。
 * 工具参数中的 shopId 为对外混淆 ID（Sqids），内部自动还原为数据库 ID。
 */
@Component
public class QueueTicketTool {

    @Resource
    private IQueueTicketService queueTicketService;

    @Resource
    private ShopMapper shopMapper;

    @Resource
    private IdObfuscator idObfuscator;

    @Tool("用户在指定商铺排队取号。需要提供商铺ID和用餐人数，返回排队号和前方等待桌数。如果用户未指定用餐人数则默认为2人。")
    public String takeQueueNumber(
            @P("商铺ID（必填，对外混淆ID）") String shopId,
            @P("用餐人数（可选，默认2，必须≥1）") Integer peopleCount) {
        Long realShopId = idObfuscator.decodeOrId(shopId);
        if (realShopId == null) {
            return "请提供要排队的商铺ID。如果用户没有指明具体商铺，请先让用户选择商铺。";
        }
        ToolParams.positive(realShopId, "商铺ID");
        if (peopleCount != null) {
            ToolParams.peopleCount(peopleCount);
        }
        if (peopleCount == null) {
            peopleCount = 2;
        }
        Long userId = getUserId();
        if (userId == null) {
            return "用户未登录，无法取号。请告知用户先登录后再取号。";
        }

        try {
            Map<String, Object> result = queueTicketService.takeNumber(realShopId, peopleCount, null);
            return JSONUtil.toJsonPrettyStr(result);
        } catch (Exception e) {
            return "取号失败: " + e.getMessage();
        }
    }

    @Tool("查询当前用户在指定商铺的排队进度。如果不传商铺ID则查询用户最近的排队记录。返回排队号、前方等待桌数、当前叫号等信息。")
    public String queryMyQueueStatus(
            @P("商铺ID，可选，不传则查询最近的排队记录") Long shopId) {
        Long userId = getUserId();
        if (userId == null) {
            return "用户未登录，无法查询排队状态。请告知用户先登录后再查询。";
        }

        try {
            Map<String, Object> ticket = queueTicketService.queryMyTicket();
            if (ticket == null) {
                return "您当前没有排队记录。";
            }
            // 补店名/地址：排队记录只有 shopId，不加店名 Agent 只能靠猜，会答错店名（如把 A 店当 B 店）
            enrichShopInfo(ticket);
            obfuscateShopId(ticket);
            return JSONUtil.toJsonPrettyStr(ticket);
        } catch (Exception e) {
            return "查询排队状态失败: " + e.getMessage();
        }
    }

    @Tool("查询指定商铺的排队情况，包括当前叫号、等待桌数、等待列表。用户可以据此判断是否需要排队以及预计等待时间。")
    public String queryShopQueueStatus(
            @P("商铺ID（对外混淆ID）") String shopId) {
        Long realShopId = idObfuscator.decodeOrId(shopId);
        if (realShopId == null) {
            return "请提供商铺ID以查询排队情况。";
        }

        try {
            Map<String, Object> result = queueTicketService.queryShopQueue(realShopId);
            // 补店名（同 queryMyQueueStatus）：排队信息只有 shopId，确认提示/回答缺店名只能靠猜
            enrichShopInfo(result);
            obfuscateShopId(result);
            return JSONUtil.toJsonPrettyStr(result);
        } catch (Exception e) {
            return "查询商铺排队失败: " + e.getMessage();
        }
    }

    @Tool("取消当前用户的排队。需要提供排队记录ID。如果用户说不想排了、取消排队等，调用此工具。")
    public String cancelMyQueue(
            @P("排队记录ID") String ticketId) {
        Long userId = getUserId();
        if (userId == null) {
            return "用户未登录，无法取消排队。";
        }
        if (ticketId == null || ticketId.isEmpty()) {
            try {
                Map<String, Object> myTicket = queueTicketService.queryMyTicket();
                if (myTicket == null) {
                    return "您当前没有排队记录。";
                }
                ticketId = myTicket.get("ticketId").toString();
            } catch (Exception e) {
                return "获取排队记录失败: " + e.getMessage();
            }
        }

        try {
            boolean ok = queueTicketService.cancelTicket(ticketId);
            return ok ? "已成功取消排队。" : "取消排队失败，请稍后重试。";
        } catch (Exception e) {
            return "取消排队失败: " + e.getMessage();
        }
    }

    /** 排队记录只有 shopId，补上店名/地址，避免 Agent 猜错店名（如把 A 店当 B 店）。 */
    private void enrichShopInfo(Map<String, Object> ticket) {
        Object shopIdObj = ticket.get("shopId");
        if (shopIdObj == null) return;
        try {
            Long shopId = Long.valueOf(shopIdObj.toString());
            Shop shop = shopMapper.selectById(shopId);
            if (shop != null) {
                ticket.put("shopName", shop.getName());
                ticket.put("shopAddress", shop.getAddress());
            }
        } catch (Exception ignored) {
        }
    }

    /** 把返回结果里的 shopId 换成对外混淆 ID（ticketId 为 UUID，无需混淆）。 */
    private void obfuscateShopId(Map<String, Object> ticket) {
        if (ticket == null) return;
        Object shopIdObj = ticket.get("shopId");
        if (shopIdObj == null) return;
        try {
            Long shopId = Long.valueOf(shopIdObj.toString());
            ticket.put("shopId", idObfuscator.encode(shopId));
        } catch (Exception ignored) {
        }
    }

    private Long getUserId() {
        Long ctxUserId = com.hmdp.agent.ToolContext.getUserId();
        if (ctxUserId != null && ctxUserId > 0) return ctxUserId;
        try {
            return UserHolder.getUser() != null ? UserHolder.getUser().getId() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
