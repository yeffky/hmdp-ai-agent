package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.QueueTicket;

import java.util.Map;

public interface IQueueTicketService extends IService<QueueTicket> {

    /** 用户取号，返回排队信息 */
    Map<String, Object> takeNumber(Long shopId, Integer peopleCount, String remark);

    /** 查询当前用户当前排队状态 */
    Map<String, Object> queryMyTicket();

    /** 查询商铺排队情况（排队人数、当前叫号等） */
    Map<String, Object> queryShopQueue(Long shopId);

    /** 用户取消排队 */
    boolean cancelTicket(String ticketId);

    /** 商家叫号（叫下一个） */
    Map<String, Object> callNextNumber(Long shopId);
}
