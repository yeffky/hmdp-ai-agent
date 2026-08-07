package com.hmdp.agent.tool.param;

/**
 * 工具参数约束校验 — 在工具入口处校验 LLM 传入的参数。
 * 违反约束抛 {@link ToolParamException}（分类为 USER_FIXABLE，走 replan/ask_user），
 * 避免把非法参数（如坐标 0,0）透传到数据库/Redis 造成诡异结果。
 */
public final class ToolParams {

    private static final int MIN_TYPE_ID = 1;
    private static final int MAX_TYPE_ID = 10;
    private static final int MIN_RADIUS = 100;
    private static final int MAX_RADIUS = 50000;

    private ToolParams() {}

    /** 商家类型一级大类，仅 1-7 */
    public static void typeId(int typeId) {
        if (typeId < MIN_TYPE_ID || typeId > MAX_TYPE_ID) {
            throw uf("商家类型ID无效（可选值 1-7，当前为 " + typeId + "），请修正后重试");
        }
    }

    /** 经纬度：禁止 0,0，且必须在合法范围内 */
    public static void lngLat(double x, double y) {
        if (Double.isNaN(x) || Double.isNaN(y)) {
            throw uf("坐标无效，请提供真实的经度、纬度");
        }
        if (x == 0.0 && y == 0.0) {
            throw uf("坐标不能为0,0，请向用户获取真实位置后重试");
        }
        if (x < -180 || x > 180) {
            throw uf("经度超出范围（-180~180）：" + x);
        }
        if (y < -90 || y > 90) {
            throw uf("纬度超出范围（-90~90）：" + y);
        }
    }

    /** 搜索半径（米），100-50000 */
    public static void radius(int radius) {
        if (radius < MIN_RADIUS || radius > MAX_RADIUS) {
            throw uf("搜索半径需在 " + MIN_RADIUS + "~" + MAX_RADIUS + " 米之间（当前 " + radius + " 米）");
        }
    }

    /** 订单状态 1-6；null 表示不过滤，放行 */
    public static void status(Integer status) {
        if (status != null && (status < 1 || status > 6)) {
            throw uf("订单状态无效（可选值 1未支付/2已支付/3已核销/4已取消/5退款中/6已退款，当前 " + status + "）");
        }
    }

    /** 正整数 ID，拒绝 null 或 ≤0 */
    public static void positive(Long v, String name) {
        if (v == null || v <= 0) {
            throw uf(name + "无效，必须为正整数，请核实后重试");
        }
    }

    /** 用餐人数：拒绝「提供了且 <1」；null 由调用方兜底默认值 */
    public static void peopleCount(Integer v) {
        if (v != null && v < 1) {
            throw uf("用餐人数无效，必须 ≥ 1（当前 " + v + "）");
        }
    }

    private static ToolParamException uf(String msg) {
        return new ToolParamException(msg);
    }
}
