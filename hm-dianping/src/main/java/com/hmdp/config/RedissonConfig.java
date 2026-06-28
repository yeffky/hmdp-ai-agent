package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host}")
    private String redisHost;

    @Value("${spring.redis.port}")
    private int redisPort;

    @Value("${spring.redis.password}")
    private String redisPassword;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort)
                .setPassword(redisPassword)
                .setTimeout(5000)          // 命令超时 5s
                .setRetryAttempts(2)       // 失败重试 2 次
                .setRetryInterval(1000)    // 重试间隔 1s
                .setPingConnectionInterval(0);  // 禁用 PING 保活（避免空闲断连报错刷日志）
        return Redisson.create(config);
    }
}
