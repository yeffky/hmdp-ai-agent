package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistoryRound {
    private Long id;
    private String userMessage;
    private String assistantMessage;
    private LocalDateTime createTime;
}
