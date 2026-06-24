package com.example.demos.web.utils;

import java.security.SecureRandom;

public class AccountGenerator {

    // 生成指定长度的随机账号
    public static String generateAccount(int length) {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder account = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            int index = random.nextInt(characters.length());
            account.append(characters.charAt(index));
        }
        return account.toString();
    }

    // 生成指定长度的随机密码
    public static String generatePassword(int length) {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_-+=<>?";
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            int index = random.nextInt(characters.length());
            password.append(characters.charAt(index));
        }
        return password.toString();
    }

    public static void main(String[] args) {
        // 生成账号和密码
        String account = generateAccount(8); // 假设账号长度为8
        String password = generatePassword(12); // 假设密码长度为12

        // 输出生成的账号和密码
        System.out.println("Generated Account: " + account);
        System.out.println("Generated Password: " + password);
    }
}

