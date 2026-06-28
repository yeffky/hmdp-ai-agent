package com.hmdp.agent.graph.state;

import org.bsc.langgraph4j.serializer.plain_text.gson.GsonStateSerializer;

/**
 * ReActAgentState 的 Gson 序列化器。
 * 用于 Checkpoint 的序列化/反序列化。
 */
public class ReActStateSerializer extends GsonStateSerializer<ReActAgentState> {

    public ReActStateSerializer() {
        super(ReActAgentState::new);
    }
}
