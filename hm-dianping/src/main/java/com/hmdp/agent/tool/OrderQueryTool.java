package com.hmdp.agent.tool;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.utils.UserHolder;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class OrderQueryTool {

    @Resource
    private VoucherOrderMapper voucherOrderMapper;

    @Tool("查询当前用户的优惠券订单，返回订单列表。可用于回答用户关于订单的询问。")
    public String queryMyOrders(@P("可选的状态过滤: 1未支付/2已支付/3已核销/4已取消/5退款中/6已退款，不传则查询全部") Integer status) {
        Long userId = getUserId();
        if (userId == null) {
            return "用户未登录，无法查询订单。请告知用户先登录后再查询订单。";
        }

        QueryWrapper<VoucherOrder> wrapper = new QueryWrapper<VoucherOrder>()
                .eq("user_id", userId)
                .orderByDesc("create_time")
                .last("LIMIT 10");
        if (status != null) {
            wrapper.eq("status", status);
        }

        List<VoucherOrder> orders = voucherOrderMapper.selectList(wrapper);
        if (orders.isEmpty()) {
            return status != null
                    ? "该用户没有状态为" + getStatusDesc(status) + "的订单。"
                    : "该用户暂无任何订单记录。";
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (VoucherOrder o : orders) {
            Map<String, Object> item = new HashMap<>();
            item.put("订单ID", o.getId());
            item.put("代金券ID", o.getVoucherId());
            item.put("支付方式", getPayTypeDesc(o.getPayType()));
            item.put("状态", getStatusDesc(o.getStatus()));
            item.put("下单时间", o.getCreateTime() != null ? o.getCreateTime().toString() : "未知");
            item.put("支付时间", o.getPayTime() != null ? o.getPayTime().toString() : "未支付");
            result.add(item);
        }

        return JSONUtil.toJsonPrettyStr(result);
    }

    private Long getUserId() {
        try {
            return UserHolder.getUser() != null ? UserHolder.getUser().getId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String getStatusDesc(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 1: return "未支付";
            case 2: return "已支付";
            case 3: return "已核销";
            case 4: return "已取消";
            case 5: return "退款中";
            case 6: return "已退款";
            default: return "未知";
        }
    }

    private String getPayTypeDesc(Integer payType) {
        if (payType == null) return "未知";
        switch (payType) {
            case 1: return "余额支付";
            case 2: return "支付宝";
            case 3: return "微信支付";
            default: return "其他";
        }
    }
}
