package com.hmdp.agent;

/**
 * 跨线程传递用户上下文（ID + 定位坐标）。
 * Agent 图在异步线程池中执行，ThreadLocal（UserHolder）会丢失。
 * AgentNode 在执行工具前将 userId 与用户定位坐标设置到此上下文，工具通过它读取。
 *
 * <p>不替代 UserHolder —— 直接 HTTP 请求仍走 UserHolder，ToolContext 仅作为异步场景的补充通道。</p>
 */
public final class ToolContext {

    private ToolContext() {}

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    /** 用户定位坐标文本（如 "经度119.3026，纬度26.0855"），供工具（如 Text2Sql 算距离）读取 */
    private static final ThreadLocal<String> USER_LOCATION = new ThreadLocal<>();

    /** 用户当前地区 id（1拱墅区/2鼓楼区），供搜索工具按地区过滤 */
    private static final ThreadLocal<Long> DISTRICT_ID = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void setLocation(String location) {
        USER_LOCATION.set(location);
    }

    public static String getLocation() {
        return USER_LOCATION.get();
    }

    public static void setDistrictId(Long districtId) {
        DISTRICT_ID.set(districtId);
    }

    public static Long getDistrictId() {
        return DISTRICT_ID.get();
    }

    /** 解析用户定位文本（"经度119.3026，纬度26.0855"）为经度；缺失/无法解析返回 null */
    public static Double getLocationX() {
        String loc = USER_LOCATION.get();
        if (loc == null || loc.isEmpty()) return null;
        try {
            int i = loc.indexOf("经度");
            int j = loc.indexOf("，");
            if (i >= 0 && j > i) {
                return Double.parseDouble(loc.substring(i + 2, j).trim());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 解析用户定位文本为纬度；缺失/无法解析返回 null */
    public static Double getLocationY() {
        String loc = USER_LOCATION.get();
        if (loc == null || loc.isEmpty()) return null;
        try {
            int j = loc.indexOf("纬度");
            if (j >= 0) {
                return Double.parseDouble(loc.substring(j + 2).trim());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static void clear() {
        USER_ID.remove();
        USER_LOCATION.remove();
        DISTRICT_ID.remove();
    }
}
