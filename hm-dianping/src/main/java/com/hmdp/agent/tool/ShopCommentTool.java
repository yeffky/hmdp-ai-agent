package com.hmdp.agent.tool;

import com.hmdp.dto.Result;
import com.hmdp.service.IShopCommentService;
import com.hmdp.agent.tool.param.ToolParamException;
import com.hmdp.utils.IdObfuscator;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 店铺评价工具 — 查询店铺评价列表（含评分）。
 */
@Component
public class ShopCommentTool {

    @Resource
    private IShopCommentService shopCommentService;

    @Resource
    private IdObfuscator idObfuscator;

    @Tool("查询指定店铺的评价列表（含评分 rating 1-5、用户昵称/头像、内容、时间）。需提供店铺ID。返回 {list,total,hasMore}。")
    public Map<String, Object> queryShopComments(
            @P("店铺ID（对外混淆ID）") String shopId,
            @P("页码，默认1") Integer current,
            @P("每页条数，默认5") Integer size) {
        Long realShopId = idObfuscator.decodeOrId(shopId);
        if (realShopId == null) {
            throw new ToolParamException("需要提供店铺ID才能查询评价");
        }
        Result r = shopCommentService.listComments(realShopId, current, size);
        return (Map<String, Object>) r.getData();
    }
}
