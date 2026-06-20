package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.hmdp.utils.RedisConstants.*;
import javax.annotation.Resource;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@SpringBootTest
public class GenerateTokenTest {
    
//    @Resource
//    private IUserService userService;
//
//    @Resource
//    private StringRedisTemplate stringRedisTemplate;
//
//    @Test
//    public void generateToken() throws IOException {
//        // 数据库查询1000个用户信息
//        List<User> userList = userService.list(new QueryWrapper<User>().last("limit 1000"));
//        // 创建字符输出流准备写入token到文件
//        BufferedWriter br = new BufferedWriter(new FileWriter("D:\\Project\\hmdp\\hm-dianping\\hm-dianping\\src\\main\\java\\com\\hmdp\\tokens.txt"));
//        for (User user : userList) {
//            // 随机生成Token作为登录令牌
//            String token = UUID.randomUUID().toString(true);
//            // 将User对象转为Hash存储
//            UserDTO userVo = BeanUtil.copyProperties(user, UserDTO.class);
//            // 将User对象转为HashMap存储
//            Map<String, Object> userMap = BeanUtil.beanToMap(userVo, new HashMap<>(),
//                    CopyOptions.create()
//                            .setIgnoreNullValue(true)
//                            .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
//            // 保存用户token到redis，设置token有效期
//            String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
//            stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
//            stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
//            // 写入token到文件
//            br.write(token);
//            br.newLine();
//            br.flush();
//        }
//    }
}
