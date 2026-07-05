package com.hmdp.agent.graph.checkpoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.checkpoint.PostgresSaver;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 增量 checkpoint 存储 — 基于 DeltaChannel 思想，只在每个 checkpoint 中存储变化的部分。
 *
 * <h3>核心机制</h3>
 * <ul>
 *   <li><b>Delta 写入</b>：每个 checkpoint 对比 parent 状态，只存储新增的消息（appender 通道）
 *       和值发生变化的标量字段（base 通道），而非全量 state。</li>
 *   <li><b>周期性快照</b>：每 {@code SNAPSHOT_FREQUENCY} 步写入一次完整快照作为锚点，
 *       限制恢复时需要回放的 delta 数量。</li>
 *   <li><b>恢复重建</b>：从 DB 加载所有行，找到最近快照，向后回放 delta 重建全量 state。</li>
 * </ul>
 *
 * <h3>性能</h3>
 * 单次 ReAct 循环产生约 6 个 checkpoint。全量 state 约 20KB+（含 40 条消息）。
 * 使用 delta 后每行约 500B（仅 1-2 条新消息），体积缩小 40 倍。
 * 加载 6 行 delta = 3KB，远小于加载 1 行全量 = 20KB。
 */
public class DeltaPostgresSaver extends PostgresSaver {

    private static final Logger log = LoggerFactory.getLogger(DeltaPostgresSaver.class);
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final int SNAPSHOT_FREQUENCY = 5;

    /**
     * 每个线程的 step 计数器。每步递增，达到 SNAPSHOT_FREQUENCY 后写全量快照并重置。
     * 读写受 AbstractCheckpointSaver 内部 ReentrantLock 保护。
     */
    private final Map<String, Integer> stepCounters = new ConcurrentHashMap<>();

    /** 自定义 serializer，用于 encode/decode state（父类的 serializer 字段是 private） */
    private final StateSerializer<?> serializer;

    public DeltaPostgresSaver(Builder builder, StateSerializer<?> serializer) throws SQLException {
        super(builder);
        this.serializer = serializer;
    }

    // ==================== 写入：delta / snapshot ====================

    @Override
    protected void insertedCheckpoint(RunnableConfig config,
                                       LinkedList<Checkpoint> checkpoints,
                                       Checkpoint checkpoint) throws Exception {
        String threadId = threadId(config);

        int step = stepCounters.merge(threadId, 1, Integer::sum);
        boolean isFirst = checkpoints.size() <= 1;  // size=1 means only the just-pushed checkpoint
        boolean isSnapshot = isFirst || (step >= SNAPSHOT_FREQUENCY);

        Checkpoint toSave;
        String contentType;

        if (isSnapshot) {
            toSave = checkpoint;  // 全量 state
            contentType = serializer.contentType();
            stepCounters.put(threadId, 0);
            log.debug("DeltaSaver: snapshot at step {} for thread '{}'", step, threadId);
        } else {
            // 取 parent（checkpoints[1] 是上一个，checkpoints[0] 是刚 push 进去的新 checkpoint）
            Map<String, Object> parentState = checkpoints.size() > 1
                    ? checkpoints.get(1).getState()
                    : Collections.emptyMap();
            Map<String, Object> delta = computeDelta(parentState, checkpoint.getState());
            delta.put("__delta__", true);

            toSave = Checkpoint.builder()
                    .id(checkpoint.getId())
                    .nodeId(checkpoint.getNodeId())
                    .nextNodeId(checkpoint.getNextNodeId())
                    .state(delta)
                    .build();
            contentType = serializer.contentType() + ";delta";
            log.debug("DeltaSaver: delta at step {} for thread '{}', {} fields changed",
                    step, threadId, delta.size() - 1);  // -1 for __delta__ marker
        }

        // 复制父类 insertCheckpoint 逻辑（父类方法是 private，只能自己实现）
        doInsert(config, checkpoints, toSave, contentType);
    }

    // ==================== 读取：重建全量 state ====================

    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) throws Exception {
        String threadId = threadId(config);
        LinkedList<Checkpoint> rawRows = loadRawRows(config, threadId);
        if (rawRows.isEmpty()) return rawRows;

        LinkedList<Checkpoint> reconstructed = new LinkedList<>();
        Map<String, Object> accumulatedState = null;

        // 从旧到新遍历（rawRows 按 saved_at ASC 排序，iterator 从旧到新）
        Iterator<Checkpoint> it = rawRows.iterator();
        while (it.hasNext()) {
            Checkpoint row = it.next();
            Map<String, Object> rowState = row.getState();

            if (isDelta(rowState)) {
                // Delta：合并到累积状态
                if (accumulatedState == null) {
                    // 没有快照锚点，delta 从空开始（边界情况：最旧的几行都是 delta）
                    accumulatedState = new LinkedHashMap<>();
                }
                Map<String, Object> cleanDelta = new LinkedHashMap<>(rowState);
                cleanDelta.remove("__delta__");
                accumulatedState = mergeDelta(accumulatedState, cleanDelta);
            } else {
                // 全量快照：重置累积状态
                accumulatedState = new LinkedHashMap<>(rowState);
            }

            // 用重建后的全量 state 构建 checkpoint
            Checkpoint full = Checkpoint.builder()
                    .id(row.getId())
                    .nodeId(row.getNodeId())
                    .nextNodeId(row.getNextNodeId())
                    .state(deepCopy(accumulatedState))
                    .build();
            reconstructed.push(full);  // push to front (newest first)
        }

        log.debug("DeltaSaver: loaded {} raw rows, reconstructed {} checkpoints for thread '{}'",
                rawRows.size(), reconstructed.size(), threadId);
        return reconstructed;
    }

    // ==================== Delta 计算 ====================

    /**
     * 计算 newState 相对于 parentState 的增量。
     *
     * <p><b>appender 通道（messages）</b>：只包含 parent 中不存在的新消息。
     * 消息用 role+content 做去重标识。
     *
     * <p><b>base 通道（标量字段）</b>：只包含值发生变化的字段（equals 比较）。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> computeDelta(Map<String, Object> parent, Map<String, Object> current) {
        Map<String, Object> delta = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : current.entrySet()) {
            String key = entry.getKey();
            Object newVal = entry.getValue();
            Object oldVal = parent.get(key);

            if ("messages".equals(key) && newVal instanceof List) {
                // Appender 通道：差集
                List<Map<String, String>> newMsgs = (List<Map<String, String>>) newVal;
                if (oldVal instanceof List) {
                    List<Map<String, String>> oldMsgs = (List<Map<String, String>>) oldVal;
                    List<Map<String, String>> diff = messageDiff(oldMsgs, newMsgs);
                    if (!diff.isEmpty()) {
                        delta.put(key, diff);
                    }
                } else {
                    // Parent 中没有 messages，全量写入
                    delta.put(key, new ArrayList<>(newMsgs));
                }
            } else if (!Objects.equals(newVal, oldVal)) {
                // Base 通道：值变化才写入
                delta.put(key, newVal);
            }
        }

        return delta;
    }

    /**
     * 计算消息差集：newMsgs 中存在但 oldMsgs 中不存在的消息。
     * 使用 "role" + "content" 组合作为唯一标识。
     */
    private List<Map<String, String>> messageDiff(List<Map<String, String>> oldMsgs,
                                                   List<Map<String, String>> newMsgs) {
        Set<String> oldKeys = new HashSet<>();
        for (Map<String, String> msg : oldMsgs) {
            oldKeys.add(msg.getOrDefault("role", "") + ":::" + msg.getOrDefault("content", ""));
        }
        List<Map<String, String>> diff = new ArrayList<>();
        for (Map<String, String> msg : newMsgs) {
            String key = msg.getOrDefault("role", "") + ":::" + msg.getOrDefault("content", "");
            if (!oldKeys.contains(key)) {
                diff.add(new LinkedHashMap<>(msg));
            }
        }
        return diff;
    }

    /**
     * 将 delta 合并到累积状态中。
     *
     * <p>messages 列表合并：累积列表 + delta 中的新消息。
     * <p>标量字段：直接覆盖。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeDelta(Map<String, Object> accumulated, Map<String, Object> delta) {
        Map<String, Object> merged = new LinkedHashMap<>(accumulated);

        for (Map.Entry<String, Object> entry : delta.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();

            if ("messages".equals(key) && val instanceof List) {
                List<Map<String, String>> existing = merged.containsKey(key)
                        ? new ArrayList<>((List<Map<String, String>>) merged.get(key))
                        : new ArrayList<>();
                existing.addAll((List<Map<String, String>>) val);
                merged.put(key, existing);
            } else {
                merged.put(key, val);
            }
        }

        return merged;
    }

    // ==================== 数据库操作 ====================

    /**
     * 加载从最近一次全量快照（含）之后的所有行。
     * 快照用 state_content_type='application/json' 标识（delta 为 'application/json;delta'）。
     * 只加载必要的行，避免随着时间推移加载全部历史。
     */
    private LinkedList<Checkpoint> loadRawRows(RunnableConfig config, String threadId) throws Exception {
        LinkedList<Checkpoint> checkpoints = new LinkedList<>();

        // 两步查询：先找最近快照的 saved_at，再加载 >= 该时间点的所有行
        String sql = """
            SELECT c.checkpoint_id, c.node_id, c.next_node_id,
                   c.state_data->>'binaryPayload' AS base64_data, c.state_content_type
            FROM lg4jthread t
            JOIN lg4jcheckpoint c ON c.thread_id = t.thread_id
            WHERE t.thread_name = ? AND t.is_released = FALSE
              AND c.saved_at >= (
                  SELECT COALESCE(
                      MAX(c2.saved_at),
                      (SELECT MIN(c3.saved_at) FROM lg4jcheckpoint c3
                       JOIN lg4jthread t3 ON c3.thread_id = t3.thread_id
                       WHERE t3.thread_name = ? AND t3.is_released = FALSE)
                  )
                  FROM lg4jcheckpoint c2
                  JOIN lg4jthread t2 ON c2.thread_id = t2.thread_id
                  WHERE t2.thread_name = ? AND t2.is_released = FALSE
                    AND c2.state_content_type = 'application/json'
              )
            ORDER BY c.saved_at ASC
            """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, threadId);
            ps.setString(2, threadId);
            ps.setString(3, threadId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    byte[] payload = rs.getBytes(4);
                    if (payload == null) continue;
                    String contentType = rs.getString(5);
                    Map<String, Object> state = decodeState(payload, contentType);
                    Checkpoint cp = Checkpoint.builder()
                            .id(rs.getString(1))
                            .nodeId(rs.getString(2))
                            .nextNodeId(rs.getString(3))
                            .state(state)
                            .build();
                    checkpoints.add(cp);
                }
            }
        }
        return checkpoints;
    }

    /** 执行实际的 INSERT 操作（复制父类 insertCheckpoint 逻辑）。 */
    private void doInsert(RunnableConfig config, LinkedList<Checkpoint> checkpoints,
                           Checkpoint checkpoint, String contentType) throws Exception {
        String threadId = threadId(config);

        String upsertThreadSql = """
            WITH inserted AS (
                INSERT INTO lg4jthread (thread_id, thread_name, is_released)
                VALUES (?, ?, FALSE)
                ON CONFLICT (thread_name) WHERE is_released = FALSE
                DO NOTHING
                RETURNING thread_id
            )
            SELECT thread_id FROM inserted
            UNION ALL
            SELECT thread_id FROM lg4jthread
            WHERE thread_name = ? AND is_released = FALSE
            LIMIT 1
            """;

        String insertSql = """
            INSERT INTO lg4jcheckpoint(
                checkpoint_id, parent_checkpoint_id, thread_id,
                node_id, next_node_id, state_data, state_content_type)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
            """;

        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            UUID threadUUID;
            try (PreparedStatement ps = conn.prepareStatement(upsertThreadSql)) {
                ps.setObject(1, UUID.randomUUID(), Types.OTHER);
                ps.setString(2, threadId);
                ps.setString(3, threadId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalStateException("Failed to upsert thread");
                    threadUUID = rs.getObject("thread_id", UUID.class);
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setObject(1, UUID.fromString(checkpoint.getId()), Types.OTHER);
                ps.setNull(2, Types.OTHER);  // parent_checkpoint_id
                ps.setObject(3, threadUUID, Types.OTHER);
                ps.setString(4, checkpoint.getNodeId());
                ps.setString(5, checkpoint.getNextNodeId());
                ps.setString(6, encodeState(checkpoint.getState()));
                ps.setString(7, contentType);
                ps.executeUpdate();
            }

            conn.commit();
        } catch (Exception e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            throw e;
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    // ==================== 编解码 ====================

    /** 复制父类 encodeState 逻辑（父类方法是 private）。 */
    private String encodeState(Map<String, Object> data) throws Exception {
        byte[] bytes = serializer.dataToBytes(data);
        return "{\"binaryPayload\": \"" +
                Base64.getEncoder().encodeToString(bytes) + "\"}";
    }

    /** 复制父类 decodeState 逻辑（父类方法是 private）。 */
    private Map<String, Object> decodeState(byte[] binaryPayload, String contentType) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(binaryPayload);
        return serializer.dataFromBytes(bytes);
    }

    // ==================== 工具方法 ====================

    private boolean isDelta(Map<String, Object> state) {
        return Boolean.TRUE.equals(state.get("__delta__"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopy(Map<String, Object> source) {
        String json = GSON.toJson(source);
        return GSON.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
    }
}
