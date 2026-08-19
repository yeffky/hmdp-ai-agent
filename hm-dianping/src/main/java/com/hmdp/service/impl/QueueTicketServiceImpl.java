package com.hmdp.service.impl;

import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.QueueTicket;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.QueueTicketMapper;
import com.hmdp.service.IShopService;
import com.hmdp.service.IQueueTicketService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 排队取号服务 — Redis 作为运营主存，MySQL 持久化兜底。
 *
 * <h3>Redis 数据结构</h3>
 * <ul>
 *   <li>{@code queue:seq:{shopId}:{yyMMdd}} — INCR 序号计数器</li>
 *   <li>{@code queue:waiting:{shopId}:{yyMMdd}} — ZSET (score=排队号, member=userId:人次)</li>
 *   <li>{@code queue:current:{shopId}:{yyMMdd}} — STRING 当前叫号</li>
 *   <li>{@code queue:user:{userId}} — STRING 用户当前 ticketId</li>
 *   <li>{@code queue:ticket:{ticketId}} — HASH 排队详情</li>
 * </ul>
 */
@Service
public class QueueTicketServiceImpl extends ServiceImpl<QueueTicketMapper, QueueTicket> implements IQueueTicketService {

    private static final Logger log = LoggerFactory.getLogger(QueueTicketServiceImpl.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IShopService shopService;

    // ========== 取号 ==========

    @Override
    public Map<String, Object> takeNumber(Long shopId, Integer peopleCount, String remark) {
        Long userId = getUserId();
        if (userId == null) throw new RuntimeException("请先登录");
        if (shopId == null) throw new RuntimeException("商铺ID不能为空");
        if (peopleCount == null || peopleCount < 1) peopleCount = 2;

        // 0. 校验店铺是否支持排队
        Shop shop = shopService.getById(shopId);
        if (shop == null) throw new RuntimeException("商铺不存在");
        if (shop.getQueueEnabled() == null || shop.getQueueEnabled() == 0) {
            throw new RuntimeException("该商铺暂不支持排队取号");
        }

        String date = today();
        String userKey = RedisConstants.QUEUE_USER_KEY + userId;

        // 1. 检查是否已有活跃排队（防止同用户跨店重复取号）
        String existingTicketId = stringRedisTemplate.opsForValue().get(userKey);
        if (existingTicketId != null && Boolean.TRUE.equals(stringRedisTemplate.hasKey(RedisConstants.QUEUE_TICKET_KEY + existingTicketId))) {
            throw new RuntimeException("您已有排队中的记录，请先取消或完成当前排队再取号");
        }

        // 2. INCR 原子生成排队号
        String seqKey = RedisConstants.QUEUE_SEQ_KEY + shopId + ":" + date;
        Long queueNumber = stringRedisTemplate.opsForValue().increment(seqKey);
        stringRedisTemplate.expire(seqKey, RedisConstants.QUEUE_TTL, TimeUnit.SECONDS);

        // 3. ZADD 加入等待队列
        String waitingKey = RedisConstants.QUEUE_WAITING_KEY + shopId + ":" + date;
        String member = userId + ":" + peopleCount;
        stringRedisTemplate.opsForZSet().add(waitingKey, member, queueNumber);
        stringRedisTemplate.expire(waitingKey, RedisConstants.QUEUE_TTL, TimeUnit.SECONDS);

        // 4. ZRANK 计算前方等待人数（score 小于当前号的数量）
        Long aheadCount = stringRedisTemplate.opsForZSet().rank(waitingKey, member);

        // 5. HSET 存储排队详情
        String ticketId = UUID.fastUUID().toString(true);
        String ticketKey = RedisConstants.QUEUE_TICKET_KEY + ticketId;
        Map<String, String> ticketMap = new LinkedHashMap<>();
        ticketMap.put("ticketId", ticketId);
        ticketMap.put("shopId", shopId.toString());
        ticketMap.put("userId", userId.toString());
        ticketMap.put("queueNumber", queueNumber.toString());
        ticketMap.put("peopleCount", peopleCount.toString());
        ticketMap.put("status", "0");
        if (remark != null) ticketMap.put("remark", remark);
        ticketMap.put("createTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        stringRedisTemplate.opsForHash().putAll(ticketKey, ticketMap);
        stringRedisTemplate.expire(ticketKey, RedisConstants.QUEUE_TTL, TimeUnit.SECONDS);

        // 6. SET 用户当前排队
        stringRedisTemplate.opsForValue().set(userKey, ticketId, RedisConstants.QUEUE_TTL, TimeUnit.SECONDS);

        // 7. MySQL 异步持久化
        persistAsync(ticketId, shopId, userId, queueNumber.intValue(), peopleCount, 0, remark);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ticketId", ticketId);
        result.put("queueNumber", queueNumber);
        result.put("peopleCount", peopleCount);
        result.put("aheadCount", aheadCount != null ? aheadCount : 0);
        result.put("estimatedWait", aheadCount != null && aheadCount > 0
                ? "前方还有 " + aheadCount + " 桌" : "即将轮到您，请留意叫号");
        return result;
    }

    // ========== 查询我的排队 ==========

    @Override
    public Map<String, Object> queryMyTicket() {
        Long userId = getUserId();
        if (userId == null) throw new RuntimeException("请先登录");

        String userKey = RedisConstants.QUEUE_USER_KEY + userId;
        String ticketId = stringRedisTemplate.opsForValue().get(userKey);
        if (ticketId == null) return null;

        String ticketKey = RedisConstants.QUEUE_TICKET_KEY + ticketId;
        Map<Object, Object> raw = stringRedisTemplate.opsForHash().entries(ticketKey);
        if (raw.isEmpty()) return null;

        Long shopId = Long.parseLong(raw.get("shopId").toString());
        int queueNumber = Integer.parseInt(raw.get("queueNumber").toString());
        String date = parseDateFrom(raw.get("createTime"));
        String waitingKey = RedisConstants.QUEUE_WAITING_KEY + shopId + ":" + date;
        String member = userId + ":" + raw.get("peopleCount");
        Long aheadCount = stringRedisTemplate.opsForZSet().rank(waitingKey, member);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ticketId", ticketId);
        result.put("shopId", shopId);
        result.put("queueNumber", queueNumber);
        result.put("peopleCount", Integer.parseInt(raw.get("peopleCount").toString()));
        result.put("status", statusDesc(Integer.parseInt(raw.get("status").toString())));
        result.put("aheadCount", aheadCount != null ? aheadCount : 0);
        result.put("createTime", raw.get("createTime"));
        return result;
    }

    // ========== 查询商铺排队 ==========

    @Override
    public Map<String, Object> queryShopQueue(Long shopId) {
        if (shopId == null) throw new RuntimeException("商铺ID不能为空");

        String date = today();
        String waitingKey = RedisConstants.QUEUE_WAITING_KEY + shopId + ":" + date;
        String currentKey = RedisConstants.QUEUE_CURRENT_KEY + shopId + ":" + date;

        String currentNumber = stringRedisTemplate.opsForValue().get(currentKey);
        Long waitingCount = stringRedisTemplate.opsForZSet().zCard(waitingKey);

        // 取前 20 个等待中的号
        Set<String> waitingMembers = stringRedisTemplate.opsForZSet()
                .range(waitingKey, 0, 19);

        List<Map<String, Object>> waitingList = new ArrayList<>();
        if (waitingMembers != null) {
            for (String m : waitingMembers) {
                String[] parts = m.split(":", 2);
                Double score = stringRedisTemplate.opsForZSet().score(waitingKey, m);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("queueNumber", score != null ? score.intValue() : 0);
                item.put("peopleCount", parts.length > 1 ? Integer.parseInt(parts[1]) : 0);
                waitingList.add(item);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("shopId", shopId);
        result.put("currentNumber", currentNumber != null ? Integer.parseInt(currentNumber) : 0);
        result.put("waitingCount", waitingCount != null ? waitingCount : 0);
        result.put("waitingList", waitingList);
        return result;
    }

    // ========== 取消排队 ==========

    @Override
    public boolean cancelTicket(String ticketId) {
        Long userId = getUserId();
        if (userId == null) throw new RuntimeException("请先登录");

        String userKey = RedisConstants.QUEUE_USER_KEY + userId;
        String myTicketId = stringRedisTemplate.opsForValue().get(userKey);
        if (myTicketId == null || !myTicketId.equals(ticketId)) {
            throw new RuntimeException("只能取消自己的排队");
        }

        String ticketKey = RedisConstants.QUEUE_TICKET_KEY + myTicketId;
        Map<Object, Object> raw = stringRedisTemplate.opsForHash().entries(ticketKey);
        if (raw.isEmpty()) throw new RuntimeException("排队记录不存在");

        String status = (String) raw.get("status");
        if (!"0".equals(status)) throw new RuntimeException("当前状态不可取消");

        // 从等待队列移除
        Long shopId = Long.parseLong(raw.get("shopId").toString());
        String date = parseDateFrom(raw.get("createTime"));
        String waitingKey = RedisConstants.QUEUE_WAITING_KEY + shopId + ":" + date;
        String member = userId + ":" + raw.get("peopleCount");
        stringRedisTemplate.opsForZSet().remove(waitingKey, member);

        // 更新状态
        stringRedisTemplate.opsForHash().put(ticketKey, "status", "2");
        stringRedisTemplate.delete(userKey);

        // MySQL 同步更新
        updateMySqlStatus(myTicketId, 2);

        return true;
    }

    // ========== 叫号 ==========

    @Override
    public Map<String, Object> callNextNumber(Long shopId) {
        if (shopId == null) throw new RuntimeException("商铺ID不能为空");

        String date = today();
        String waitingKey = RedisConstants.QUEUE_WAITING_KEY + shopId + ":" + date;

        // ZPOPMIN 原子操作：弹出最小 score 的 member，避免并发叫到同一号
        Set<ZSetOperations.TypedTuple<String>> popped =
                stringRedisTemplate.opsForZSet().popMin(waitingKey, 1);
        if (popped == null || popped.isEmpty()) {
            throw new RuntimeException("当前没有排队顾客");
        }
        ZSetOperations.TypedTuple<String> tuple =
                popped.iterator().next();
        String member = tuple.getValue();
        int queueNumber = tuple.getScore() != null ? tuple.getScore().intValue() : 0;

        String[] parts = member.split(":", 2);
        Long userId = Long.parseLong(parts[0]);

        // 更新当前叫号
        String currentKey = RedisConstants.QUEUE_CURRENT_KEY + shopId + ":" + date;
        stringRedisTemplate.opsForValue().set(currentKey, String.valueOf(queueNumber),
                RedisConstants.QUEUE_TTL, TimeUnit.SECONDS);

        // 清除用户排队标记 + 更新 ticket 状态
        String userKey = RedisConstants.QUEUE_USER_KEY + userId;
        String ticketId = stringRedisTemplate.opsForValue().get(userKey);
        stringRedisTemplate.delete(userKey);
        if (ticketId != null) {
            stringRedisTemplate.opsForHash().put(RedisConstants.QUEUE_TICKET_KEY + ticketId, "status", "1");
            updateMySqlStatus(ticketId, 1);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queueNumber", queueNumber);
        result.put("peopleCount", parts.length > 1 ? Integer.parseInt(parts[1]) : 0);
        result.put("message", "请 " + queueNumber + " 号顾客入座");
        return result;
    }

    // ========== 内部工具 ==========

    private static String today() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
    }

    private static String parseDateFrom(Object createTime) {
        if (createTime == null) return today();
        String ts = createTime.toString().substring(0, 10); // yyyy-MM-dd
        return ts.replace("-", "").substring(2); // yyMMdd
    }

    private void updateMySqlStatus(String ticketId, int status) {
        try {
            Object mysqlId = stringRedisTemplate.opsForHash()
                    .get(RedisConstants.QUEUE_TICKET_KEY + ticketId, "mysqlId");
            if (mysqlId != null) {
                QueueTicket ticket = getById(Long.parseLong(mysqlId.toString()));
                if (ticket != null) {
                    ticket.setStatus(status);
                    updateById(ticket);
                }
            }
        } catch (Exception e) {
            log.warn("MySQL 状态同步失败: ticketId={}, status={}", ticketId, status, e);
        }
    }

    private void persistAsync(String ticketId, Long shopId, Long userId,
                              int queueNumber, int peopleCount, int status, String remark) {
        try {
            QueueTicket t = new QueueTicket();
            t.setShopId(shopId);
            t.setUserId(userId);
            t.setQueueNumber(queueNumber);
            t.setPeopleCount(peopleCount);
            t.setStatus(status);
            t.setRemark(remark);
            save(t); // MySQL 自增 ID
            // 回写 MySQL ID 到 Redis，后续取消/完成时可用于同步
            stringRedisTemplate.opsForHash().put(
                    RedisConstants.QUEUE_TICKET_KEY + ticketId, "mysqlId", t.getId().toString());
        } catch (Exception e) {
            log.warn("MySQL 持久化失败: ticketId={}", ticketId, e);
        }
    }

    private Long getUserId() {
        // Agent 异步执行时 ThreadLocal 丢失，优先从 ToolContext 读取
        Long ctxUserId = com.hmdp.agent.ToolContext.getUserId();
        if (ctxUserId != null && ctxUserId > 0) return ctxUserId;
        try {
            return UserHolder.getUser() != null ? UserHolder.getUser().getId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String statusDesc(int status) {
        switch (status) {
            case 0: return "排队中";
            case 1: return "已叫号";
            case 2: return "已取消";
            case 3: return "已完成";
            default: return "未知";
        }
    }
}
