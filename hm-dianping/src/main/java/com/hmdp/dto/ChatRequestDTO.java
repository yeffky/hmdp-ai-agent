package com.hmdp.dto;

import lombok.Data;

@Data
public class ChatRequestDTO {
    private String sessionId;
    private String message;
    /** 用户定位经度（可选，如 "119.3026"）；不传则该入口无定位上下文 */
    private String centerX;
    /** 用户定位纬度（可选，如 "26.0855"） */
    private String centerY;
    /** 用户当前地区 id（可选，1拱墅/2鼓楼） */
    private String districtId;
}
