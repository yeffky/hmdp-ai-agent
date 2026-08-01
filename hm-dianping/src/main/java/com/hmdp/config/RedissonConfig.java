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
                .setTimeout(10000)          // 命令超时 10s（远程 Redis 公网延迟高）
                .setConnectTimeout(10000)   // 建连超时 10s
                .setRetryAttempts(3)        // 失败重试 3 次
                .setRetryInterval(1500)     // 重试间隔 1.5s
                .setPingConnectionInterval(30000) // 30s PING 保活——提前感知死连接并重建，避免命令撞上被 NAT 静默断开的连接
                .setIdleConnectionTimeout(30000)  // 空闲 30s 主动回收，不让 NAT/防火墙先杀连接
                .setKeepAlive(true)         // TCP keepalive
                .setTcpNoDelay(true);       // 关闭 Nagle，降低小命令（锁）延迟
        return Redisson.create(config);
    }
}
