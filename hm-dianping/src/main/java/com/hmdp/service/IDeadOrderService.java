package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.DeadOrder;

/**
 * 秒杀死信订单记录 服务类
 */
public interface IDeadOrderService extends IService<DeadOrder> {

    /**
     * 手动重放：将已落库的死信重新投递回秒杀订单队列
     */
    void redeliver(Long id);

    /**
     * 确认丢弃：将死信标记为已确认丢弃
     */
    void discard(Long id);
}
