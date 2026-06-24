package com.example.demos.web.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *  mybatis-plus 分页插件
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 分页插件
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        //初始化
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        //添加配置
        //PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        //paginationInnerInterceptor.setMaxLimit(1000L);//最大查询页数
        //paginationInnerInterceptor.setOverflow(true);//查到最后之后继续查会跳转到第一页
        //添加分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

}
