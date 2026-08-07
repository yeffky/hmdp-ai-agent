package com.hmdp.agent.graph.error;

import com.hmdp.agent.tool.param.ToolParamException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 参数校验异常 → USER_FIXABLE 的错误分类确定性验证。
 */
class ErrorClassifierToolParamTest {

    @Test
    void toolParamExceptionMapsToUserFixable() {
        ErrorCategory cat = ErrorClassifier.classifyException(
                new ToolParamException("坐标不能为0,0"));
        assertEquals(ErrorCategory.USER_FIXABLE, cat);
    }

    @Test
    void toolExceptionDefaultsToFatal() {
        ErrorCategory cat = ErrorClassifier.classifyException(
                new ToolException("geoSearch", "Geo搜索失败: boom"));
        assertEquals(ErrorCategory.FATAL, cat);
    }

    @Test
    void wrappedToolParamExceptionStillUserFixable() {
        ErrorCategory cat = ErrorClassifier.classifyException(
                new RuntimeException("outer", new ToolParamException("半径超出范围")));
        assertEquals(ErrorCategory.USER_FIXABLE, cat);
    }
}
