package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.AgentTrace;
import com.hmdp.mapper.AgentTraceMapper;
import com.hmdp.service.IAgentTraceService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AgentTraceServiceImpl extends ServiceImpl<AgentTraceMapper, AgentTrace>
        implements IAgentTraceService {

    @Override
    public Result listTraces(Long userId, Integer current, Integer size) {
        int s = (size == null || size < 1) ? 20 : size;
        Page<AgentTrace> page = query()
                .eq(userId != null, "user_id", userId)
                .orderByDesc("create_time")
                .orderByDesc("id")
                .page(new Page<>(current == null ? 1 : current, s));
        Map<String, Object> res = new HashMap<>();
        res.put("list", page.getRecords());
        res.put("total", page.getTotal());
        res.put("hasMore", page.getCurrent() * page.getSize() < page.getTotal());
        return Result.ok(res);
    }

    @Override
    public Result traceDetail(Long userId, String traceId) {
        if (traceId == null || traceId.isEmpty()) return Result.fail("traceId 不能为空");
        AgentTrace trace = query()
                .eq("trace_id", traceId)
                .eq(userId != null, "user_id", userId)
                .orderByDesc("id").last("limit 1").one();
        return Result.ok(trace);
    }
}
