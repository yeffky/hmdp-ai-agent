package com.hmdp.agent.memory.reflection;

import java.sql.Timestamp;

/**
 * Reflexion 教训记忆条目 —— 一条"失败原因 + 正确做法"，供相似查询复用。
 */
public class Reflection {

    private Long id;
    private String sessionId;
    private String domain;
    private String userQuery;
    private String lesson;
    private String keywords;
    private Timestamp createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getUserQuery() { return userQuery; }
    public void setUserQuery(String userQuery) { this.userQuery = userQuery; }
    public String getLesson() { return lesson; }
    public void setLesson(String lesson) { this.lesson = lesson; }
    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }
    public Timestamp getCreateTime() { return createTime; }
    public void setCreateTime(Timestamp createTime) { this.createTime = createTime; }
}
