package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IAgentTraceService;
import com.hmdp.utils.UserHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 可观测性后台：查看执行轨迹 + LLM 调用级时间线 + 观测大盘聚合。
 *
 * <p>数据隔离：所有接口按当前登录用户（UserHolder）过滤，
 * 只能看到自己的 agent_trace / agent_llm_trace（后者无 user_id 列，通过 JOIN agent_trace 归属）。
 */
@RestController
@RequestMapping("/admin/trace")
public class AgentTraceController {

    private static final Logger log = LoggerFactory.getLogger(AgentTraceController.class);

    @Resource
    private IAgentTraceService agentTraceService;

    @Resource
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @PostConstruct
    void init() {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    private Long uid() {
        return UserHolder.getUser() != null ? UserHolder.getUser().getId() : null;
    }

    /** 分页查询当前用户轨迹列表（按时间倒序） */
    @GetMapping("/list")
    public Result list(@RequestParam(value = "current", defaultValue = "1") Integer current,
                       @RequestParam(value = "size", defaultValue = "20") Integer size) {
        Long userId = uid();
        return userId == null ? Result.fail("请先登录") : agentTraceService.listTraces(userId, current, size);
    }

    /** 按 traceId 查当前用户的一条详情（越权返回 null） */
    @GetMapping("/{traceId}")
    public Result detail(@PathVariable String traceId) {
        Long userId = uid();
        return userId == null ? Result.fail("请先登录") : agentTraceService.traceDetail(userId, traceId);
    }

    /** 按 traceId 查 LLM 调用级时间线（先校验轨迹归属当前用户） */
    @GetMapping("/llm/{traceId}")
    public Result llmTrace(@PathVariable String traceId) {
        Long userId = uid();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_trace WHERE trace_id = ? AND user_id = ?",
                Integer.class, traceId, userId);
        if (cnt == null || cnt == 0) {
            return Result.fail("无权访问该轨迹");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, stage, input_tokens, output_tokens, duration_ms, model, prompt, response, create_time " +
                "FROM agent_llm_trace WHERE trace_id = ? ORDER BY id", traceId);
        return Result.ok(rows);
    }

    /** 大盘 L1 概览：当前用户的请求数/平均延迟/成功率/LLM 调用数/总 token */
    @GetMapping("/summary")
    public Result summary() {
        Long userId = uid();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        try {
            m.putAll(queryOne(
                    "SELECT COUNT(*) AS reqs, ROUND(AVG(total_ms),0) AS avg_ms, " +
                    "ROUND(SUM(status='completed')/COUNT(*)*100,1) AS success_rate " +
                    "FROM agent_trace WHERE user_id = " + userId));
            m.putAll(queryOne(
                    "SELECT COUNT(*) AS llm_calls, " +
                    "COALESCE(SUM(COALESCE(t.input_tokens,0)+COALESCE(t.output_tokens,0)),0) AS tokens " +
                    "FROM agent_llm_trace t JOIN agent_trace a ON t.trace_id = a.trace_id " +
                    "WHERE a.user_id = " + userId));
        } catch (Exception e) {
            log.warn("trace summary failed: {}", e.getMessage());
        }
        return Result.ok(m);
    }

    /** 大盘 L2 趋势：range=24h(按小时)|7d|30d(按天)，当前用户请求量/延迟 + LLM 调用数/token */
    @GetMapping("/trends")
    public Result trends(@RequestParam(value = "range", defaultValue = "24h") String range) {
        Long userId = uid();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        String bucket = "24h".equals(range) ? "%Y-%m-%d %H:00" : "%Y-%m-%d";
        int hours = "24h".equals(range) ? 24 : ("7d".equals(range) ? 24 * 7 : 24 * 30);
        Map<String, Object> m = new LinkedHashMap<>();
        try {
            m.put("requests", jdbc.queryForList(
                    "SELECT DATE_FORMAT(create_time, '" + bucket + "') AS bucket, COUNT(*) AS cnt, " +
                    "ROUND(AVG(total_ms),0) AS avg_ms FROM agent_trace " +
                    "WHERE user_id = " + userId + " AND create_time > NOW() - INTERVAL " + hours +
                    " HOUR GROUP BY bucket ORDER BY bucket"));
            m.put("llm", jdbc.queryForList(
                    "SELECT DATE_FORMAT(t.create_time, '" + bucket + "') AS bucket, COUNT(*) AS calls, " +
                    "COALESCE(SUM(COALESCE(t.input_tokens,0)+COALESCE(t.output_tokens,0)),0) AS tokens, " +
                    "ROUND(AVG(t.duration_ms),0) AS avg_ms FROM agent_llm_trace t " +
                    "JOIN agent_trace a ON t.trace_id = a.trace_id " +
                    "WHERE a.user_id = " + userId + " AND t.create_time > NOW() - INTERVAL " + hours +
                    " HOUR GROUP BY bucket ORDER BY bucket"));
        } catch (Exception e) {
            log.warn("trace trends failed: {}", e.getMessage());
        }
        return Result.ok(m);
    }

    /** 大盘 L3 分布：当前用户 LLM 调用按 stage 分布（含 token/耗时/调用数） */
    @GetMapping("/stages")
    public Result stages() {
        Long userId = uid();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        try {
            return Result.ok(jdbc.queryForList(
                    "SELECT t.stage, COUNT(*) AS calls, " +
                    "COALESCE(SUM(COALESCE(t.input_tokens,0)+COALESCE(t.output_tokens,0)),0) AS tokens, " +
                    "ROUND(AVG(t.duration_ms),0) AS avg_ms FROM agent_llm_trace t " +
                    "JOIN agent_trace a ON t.trace_id = a.trace_id " +
                    "WHERE a.user_id = " + userId + " GROUP BY t.stage ORDER BY calls DESC"));
        } catch (Exception e) {
            log.warn("trace stages failed: {}", e.getMessage());
            return Result.ok(List.of());
        }
    }

    private Map<String, Object> queryOne(String sql) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        return rows.isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(rows.get(0));
    }
}
