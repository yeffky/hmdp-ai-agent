package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IQueueTicketService;
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

    /** 用户取号 */
    @PostMapping("/take")
    public Result takeNumber(@RequestBody Map<String, Object> body) {
        try {
            Long shopId = toLong(body.get("shopId"));
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

    /** 查询商铺排队情况 */
    @GetMapping("/shop/{shopId}")
    public Result queryShopQueue(@PathVariable Long shopId) {
        try {
            Map<String, Object> info = queueTicketService.queryShopQueue(shopId);
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

    /** 商家叫号 */
    @PutMapping("/call/{shopId}")
    public Result callNextNumber(@PathVariable Long shopId) {
        try {
            Map<String, Object> result = queueTicketService.callNextNumber(shopId);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("叫号失败", e);
            return Result.fail(e.getMessage());
        }
    }

    private static Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).longValue();
        return Long.parseLong(val.toString());
    }
}
