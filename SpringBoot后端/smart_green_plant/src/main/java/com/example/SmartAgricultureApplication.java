package com.example;


import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableTransactionManagement //开启注解方式的事务管理
@SpringBootApplication
@MapperScan("com.example.demos.web.mapper")
public class SmartAgricultureApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartAgricultureApplication.class, args);
    }

}
