package com.example.demos.web.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailUtil {
    @Autowired
    private JavaMailSender javaMailSender;

    /**
     * 发送简单邮件
     *
     * @param to      收件人邮箱
     * @param subject 邮件主题
     * @param text    邮件内容
     */
    public void sendSimpleEmail(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("3073561696@qq.com");  // 发件人邮箱
        message.setTo(to);  // 收件人邮箱
        message.setSubject(subject);  // 邮件主题
        message.setText(text);  // 邮件内容
        javaMailSender.send(message);
    }
}
