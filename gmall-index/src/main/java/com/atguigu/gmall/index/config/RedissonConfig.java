package com.atguigu.gmall.index.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){
        Config config = new Config();
        //可以用"redis://"来启用SSL连接
        config.useSingleServer().setAddress("redis://139.224.48.113:10210");
        return Redisson.create(config);
    }
}
