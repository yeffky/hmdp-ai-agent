package com.hmdp.agent.graph.error;

import com.hmdp.agent.tool.param.ToolParamException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ErrorClassifier regex-first 分类行为验证。
 */
class ErrorClassifierTest {

    @Test
    void messageKeywordBeatsClassName() {
        // 消息含 timeout → 即使类名是 IllegalArgumentException 也判 RETRYABLE（正则优先）
        ErrorCategory cat = ErrorClassifier.classifyException(
                new IllegalArgumentException("request timeout after 30s"));
        assertEquals(ErrorCategory.RETRYABLE, cat);
    }

    @Test
    void userFixableMessageKeyword() {
        assertEquals(ErrorCategory.USER_FIXABLE,
                ErrorClassifier.classifyException(new RuntimeException("参数不能为空")));
        assertEquals(ErrorCategory.USER_FIXABLE,
                ErrorClassifier.classifyException(new RuntimeException("缺少参数: typeId")));
    }

    @Test
    void fatalMessageKeyword() {
        assertEquals(ErrorCategory.FATAL,
                ErrorClassifier.classifyException(new RuntimeException("SQL syntax error near 'SELECT'")));
        assertEquals(ErrorCategory.FATAL,
                ErrorClassifier.classifyException(new RuntimeException("column 'x' does not exist")));
    }

    @Test
    void retryableByClassNameWhenMessageEmpty() {
        ErrorCategory cat = ErrorClassifier.classifyException(
                new java.net.SocketTimeoutException());
        assertEquals(ErrorCategory.RETRYABLE, cat);
    }

    @Test
    void toolParamExceptionUserFixable() {
        assertEquals(ErrorCategory.USER_FIXABLE,
                ErrorClassifier.classifyException(new ToolParamException("坐标不能为0,0")));
    }

    @Test
    void plainRuntimeExceptionDefaultsToFatal() {
        assertEquals(ErrorCategory.FATAL,
                ErrorClassifier.classifyException(new RuntimeException("boom")));
    }

    @Test
    void nullIsFatal() {
        assertEquals(ErrorCategory.FATAL, ErrorClassifier.classifyException(null));
    }

    @Test
    void causeChainIsWalked() {
        ErrorCategory cat = ErrorClassifier.classifyException(
                new RuntimeException("outer", new IllegalStateException("inner 权限不足")));
        assertEquals(ErrorCategory.USER_FIXABLE, cat);
    }
}
