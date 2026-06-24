package com.example.demos.web.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@Slf4j
public class RedisConfiguration {
    @Bean
    public RedisTemplate redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        log.info("开始创建redis模板对象");

        RedisTemplate redisTemplate = new RedisTemplate();

        //设置redis连接工厂对象
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        //设置redis key的序列化器
        redisTemplate.setKeySerializer(new StringRedisSerializer());

        return redisTemplate;
    }
    //@Bean
    //public RedisConnectionFactory redisConnectionFactory() {
    //    // 使用Jedis或Lettuce连接工厂，这里以Lettuce为例
    //    return new LettuceConnectionFactory("127.0.0.1", 6379);
    //}

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate(factory);
        // 设置序列化器（默认就是字符串序列化器，此处可省略）
        // template.setKeySerializer(RedisSerializer.string());
        // template.setValueSerializer(RedisSerializer.string());
        // template.setHashKeySerializer(RedisSerializer.string());
        // template.setHashValueSerializer(RedisSerializer.string());
        return template;
    }


}
