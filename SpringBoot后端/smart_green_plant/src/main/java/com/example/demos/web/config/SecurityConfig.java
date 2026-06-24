package com.example.demos.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
//@Configuration
//@EnableWebSecurity
//public class SecurityConfig extends WebSecurityConfigurerAdapter {
//
//    //bug
//    @Bean
//    public PasswordEncoder passwordEncoder() {
//        return new BCryptPasswordEncoder();
//    }
//
//    @Override
//    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
//        auth.inMemoryAuthentication()
//                .withUser("admin")
//                .password(passwordEncoder().encode("admin123")) // 使用BCrypt加密
//                .roles("USER");
//    }
//
//    @Override
//    protected void configure(HttpSecurity http) throws Exception {
//        http.csrf().disable()
//                .authorizeRequests()
//                .antMatchers(HttpMethod.OPTIONS, "/**").permitAll() // 放行OPTIONS请求
//                .antMatchers("/user/login", "/user/register").permitAll() // 允许匿名访问
//                .anyRequest().authenticated(); // 其他请求需要认证
//    }
//}