package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.AgentTrace;

public interface IAgentTraceService extends IService<AgentTrace> {

    /** 分页查询当前用户的轨迹列表（按时间倒序），返回 {list,total,hasMore} */
    Result listTraces(Long userId, Integer current, Integer size);

    /** 按 traceId 查询当前用户的一条轨迹详情（越权返回 null） */
    Result traceDetail(Long userId, String traceId);
}
