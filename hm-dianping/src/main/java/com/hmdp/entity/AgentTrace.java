package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 会话执行轨迹（可观测性后台）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("agent_trace")
public class AgentTrace implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 请求 traceId（X-Trace-Id） */
    private String traceId;

    private Long userId;

    /** 用户问题 */
    private String query;

    /** 节点执行步骤序列 JSON：[{node,tool,at}] */
    private String stepsJson;

    private Integer nodeCount;

    private Long totalMs;

    /** completed / error */
    private String status;

    private String errorMsg;

    private LocalDateTime createTime;
}
