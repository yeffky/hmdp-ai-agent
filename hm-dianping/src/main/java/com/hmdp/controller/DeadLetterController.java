package com.hmdp.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.DeadOrder;
import com.hmdp.service.IDeadOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 秒杀死信订单管理 API
 */
@Slf4j
@RestController
@RequestMapping("/dead-letter")
public class DeadLetterController {

    @Resource
    private IDeadOrderService deadOrderService;

    /** 分页查询死信记录 */
    @GetMapping("/list")
    public Result list(@RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "20") Integer size) {
        Page<DeadOrder> p = new Page<>(Math.max(page, 1), Math.min(size, 100));
        Page<DeadOrder> result = deadOrderService.page(p,
                new QueryWrapper<DeadOrder>().orderByDesc("create_time"));
        return Result.ok(result.getRecords(), result.getTotal());
    }

    /** 手动重放：将死信打回秒杀订单队列重新消费 */
    @PostMapping("/{id}/redeliver")
    public Result redeliver(@PathVariable Long id) {
        try {
            deadOrderService.redeliver(id);
            return Result.ok();
        } catch (Exception e) {
            log.error("死信重放失败: id={}", id, e);
            return Result.fail(e.getMessage());
        }
    }

    /** 确认丢弃：标记死信为已处理，不再重放 */
    @PostMapping("/{id}/discard")
    public Result discard(@PathVariable Long id) {
        try {
            deadOrderService.discard(id);
            return Result.ok();
        } catch (Exception e) {
            log.error("死信丢弃失败: id={}", id, e);
            return Result.fail(e.getMessage());
        }
    }
}
