package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IQueueTicketService;
import com.hmdp.utils.IdObfuscator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/queue-ticket")
public class QueueTicketController {

    private static final Logger log = LoggerFactory.getLogger(QueueTicketController.class);

    @Resource
    private IQueueTicketService queueTicketService;

    @Resource
    private IdObfuscator idObfuscator;

    /** 用户取号；shopId 支持对外混淆 ID 或数据库真实 ID */
    @PostMapping("/take")
    public Result takeNumber(@RequestBody Map<String, Object> body) {
        try {
            Long shopId = idObfuscator.decodeOrId(body.get("shopId") == null ? null : String.valueOf(body.get("shopId")));
            Integer peopleCount = body.get("peopleCount") != null
                    ? ((Number) body.get("peopleCount")).intValue() : 2;
            String remark = body.get("remark") != null ? body.get("remark").toString() : null;
            Map<String, Object> info = queueTicketService.takeNumber(shopId, peopleCount, remark);
            return Result.ok(info);
        } catch (Exception e) {
            log.error("取号失败", e);
            return Result.fail(e.getMessage());
        }
    }

    /** 查询我的排队 */
    @GetMapping("/my")
    public Result queryMyTicket() {
        try {
            Map<String, Object> ticket = queueTicketService.queryMyTicket();
            if (ticket == null) {
                return Result.fail("当前没有排队记录");
            }
            return Result.ok(ticket);
        } catch (Exception e) {
            log.error("查询排队失败", e);
            return Result.fail(e.getMessage());
        }
    }

    /** 查询商铺排队情况；shopId 支持对外混淆 ID 或数据库真实 ID */
    @GetMapping("/shop/{shopId}")
    public Result queryShopQueue(@PathVariable String shopId) {
        try {
            Long realShopId = idObfuscator.decodeOrId(shopId);
            if (realShopId == null) {
                return Result.fail("店铺不存在");
            }
            Map<String, Object> info = queueTicketService.queryShopQueue(realShopId);
            return Result.ok(info);
        } catch (Exception e) {
            log.error("查询商铺排队失败", e);
            return Result.fail(e.getMessage());
        }
    }

    /** 取消排队 */
    @PutMapping("/cancel/{ticketId}")
    public Result cancelTicket(@PathVariable String ticketId) {
        try {
            boolean ok = queueTicketService.cancelTicket(ticketId);
            return ok ? Result.ok("已取消排队") : Result.fail("取消失败");
        } catch (Exception e) {
            log.error("取消排队失败", e);
            return Result.fail(e.getMessage());
        }
    }

    /** 商家叫号；shopId 支持对外混淆 ID 或数据库真实 ID */
    @PutMapping("/call/{shopId}")
    public Result callNextNumber(@PathVariable String shopId) {
        try {
            Long realShopId = idObfuscator.decodeOrId(shopId);
            if (realShopId == null) {
                return Result.fail("店铺不存在");
            }
            Map<String, Object> result = queueTicketService.callNextNumber(realShopId);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("叫号失败", e);
            return Result.fail(e.getMessage());
        }
    }
}
