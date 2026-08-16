package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistoryRound {
    private Long id;
    private String userMessage;
    private String assistantMessage;
    /** 当轮店铺卡片（统一商家卡数组），历史恢复时前端重建卡片渲染 */
    private List<Map<String, Object>> cards;
    /** 回答结构块：[{type:"text",text} | {type:"card",id}]，按出现顺序——历史重建时卡片插到对应位置而非堆末尾 */
    private List<Map<String, Object>> blocks;
    private LocalDateTime createTime;
}
