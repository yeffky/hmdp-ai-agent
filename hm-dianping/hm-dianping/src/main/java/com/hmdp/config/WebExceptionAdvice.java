package com.hmdp.config;

import com.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.ConnectException;

@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    /** Redis 超时/连接失败 — 不崩，返回友好提示 */
    @ExceptionHandler({QueryTimeoutException.class, RedisConnectionFailureException.class,
                       DataAccessException.class})
    public Result handleRedisException(DataAccessException e) {
        log.warn("数据库/Redis 暂时不可用: {}", e.getMessage());
        return Result.fail("服务繁忙，请稍后重试");
    }

    /** 网络连接失败 */
    @ExceptionHandler(ConnectException.class)
    public Result handleConnectException(ConnectException e) {
        log.warn("网络连接失败: {}", e.getMessage());
        return Result.fail("网络异常，请稍后重试");
    }

    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        log.error(e.toString(), e);
        return Result.fail("服务器异常");
    }
}
