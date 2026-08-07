package com.hmdp.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;
    /** refreshToken 会话（可吊销）：key=login:refresh:{refreshToken}，TTL 与 refresh JWT 对齐 */
    public static final String LOGIN_REFRESH_KEY = "login:refresh:";
    public static final Long LOGIN_REFRESH_TTL_DAYS = 7L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";

    // ========== 排队取号 ==========
    /** 当日排队序号计数器，key: queue:seq:{shopId}:{date} */
    public static final String QUEUE_SEQ_KEY = "queue:seq:";
    /** 等待队列 ZSET，score=排队号, member=userId:queueNumber:peopleCount */
    public static final String QUEUE_WAITING_KEY = "queue:waiting:";
    /** 当前叫号，key: queue:current:{shopId}:{date} */
    public static final String QUEUE_CURRENT_KEY = "queue:current:";
    /** 用户当前排队 ticketId，key: queue:user:{userId} */
    public static final String QUEUE_USER_KEY = "queue:user:";
    /** 排队详情 Hash，key: queue:ticket:{ticketId} */
    public static final String QUEUE_TICKET_KEY = "queue:ticket:";
    /** 排队相关 Key 的过期秒数（1天 + 1小时缓冲） */
    public static final Long QUEUE_TTL = 90000L;
}
