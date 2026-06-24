package com.example.demos.web.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import javax.servlet.http.HttpServletRequest;

public class LocationUtil {

    // 外部服务列表，用于获取公共IP地址
    private static final List<String> IP_SERVICES = Arrays.asList(
            "https://api.ipify.org",
            "https://checkip.amazonaws.com/",
            "https://ifconfig.me/ip",
            "https://icanhazip.com/",
            "https://ipecho.net/plain"
    );

    // 连接和读取超时时间（毫秒）
    private static final int CONNECT_TIMEOUT = 5000; // 5秒
    private static final int READ_TIMEOUT = 5000;    // 5秒

    // 缓存公共IP和缓存时间
    private static String cachedIp = "";
    private static long cacheTimestamp = 0;
    private static final long CACHE_DURATION = 5 * 60 * 1000; // 5分钟

    /**
     * 获取客户端IP地址，如果是私有IP或回环地址，则获取服务器的公共IP地址。
     *
     * @param request HttpServletRequest 对象
     * @return 公共IP地址字符串，如果无法获取则返回空字符串
     */
    public static String getEffectiveIp(HttpServletRequest request) {
        String clientIp = getClientIp(request);
        if (isPrivateOrLoopbackIp(clientIp)) {
            clientIp = getPublicIp();
        }
        return clientIp;
    }

    /**
     * 获取客户端IP地址
     *
     * @param request HttpServletRequest 对象
     * @return 客户端IP地址字符串
     */
    private static String getClientIp(HttpServletRequest request) {
        String ip = null;

//        // 优先从头部获取IP
//        String xForwardedFor = request.getHeader("X-Forwarded-For");
//        if (isValidIp(xForwardedFor)) {
//            ip = extractFirstIp(xForwardedFor);
//        }
//
//        if (ip == null) {
//            String proxyClientIp = request.getHeader("Proxy-Client-IP");
//            if (isValidIp(proxyClientIp)) {
//                ip = proxyClientIp;
//            }
//        }
//
//        if (ip == null) {
//            String wlProxyClientIp = request.getHeader("WL-Proxy-Client-IP");
//            if (isValidIp(wlProxyClientIp)) {
//                ip = wlProxyClientIp;
//            }
//        }

        if (ip == null) {
            ip = request.getRemoteAddr();
        }

        // 处理IPv6映射的IPv4地址和IPv6回环地址
        if (ip != null) {
            ip = convertToIPv4(ip);
        }

        return ip;
    }

    /**
     * 判断IP地址是否有效
     *
     * @param ip IP地址字符串
     * @return 如果IP有效则返回true，否则返回false
     */
    private static boolean isValidIp(String ip) {
        return ip != null && ip.length() != 0 && !"unknown".equalsIgnoreCase(ip);
    }

    /**
     * 从逗号分隔的IP列表中提取第一个IP地址
     *
     * @param ipList IP地址列表字符串
     * @return 第一个IP地址字符串
     */
    private static String extractFirstIp(String ipList) {
        if (ipList.contains(",")) {
            return ipList.split(",")[0].trim();
        }
        return ipList.trim();
    }

    /**
     * 将IPv6地址转换为IPv4地址，如果是IPv6回环地址则返回127.0.0.1
     *
     * @param ip 原始IP地址字符串
     * @return 转换后的IPv4地址字符串
     */
    private static String convertToIPv4(String ip) {
        if (ip.startsWith("::ffff:")) {
            return ip.substring(7);
        }
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }
        if (isIPv4(ip)) {
            return ip;
        }
        return ip;
    }

    /**
     * 判断是否为IPv4地址
     *
     * @param ip IP地址字符串
     * @return 如果是IPv4地址则返回true，否则返回false
     */
    private static boolean isIPv4(String ip) {
        try {
            InetAddress inet = InetAddress.getByName(ip);
            return inet.getHostAddress().equals(ip) && inet instanceof java.net.Inet4Address;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断IP地址是否为私有IP或回环地址
     *
     * @param ip IP地址字符串
     * @return 如果是私有IP或回环地址则返回true，否则返回false
     */
    private static boolean isPrivateOrLoopbackIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return true;
        }
        try {
            InetAddress inet = InetAddress.getByName(ip);
            return inet.isAnyLocalAddress() || inet.isLoopbackAddress() || inet.isSiteLocalAddress();
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 获取公共IP地址，使用缓存以减少外部请求
     *
     * @return 公共IP地址字符串，如果无法获取则返回空字符串
     */
    public static String getPublicIp() {
        long currentTime = System.currentTimeMillis();
        if (!cachedIp.isEmpty() && (currentTime - cacheTimestamp) < CACHE_DURATION) {
            return cachedIp;
        }

        for (String serviceUrl : IP_SERVICES) {
            try {
                String ip = fetchIpFromService(serviceUrl);
                if (ip != null && !ip.isEmpty()) {
                    cachedIp = ip.trim();
                    cacheTimestamp = currentTime;
                    return cachedIp;
                }
            } catch (Exception e) {
                // 记录异常并尝试下一个服务
                System.err.println("无法从服务 " + serviceUrl + " 获取公共IP: " + e.getMessage());
            }
        }
        return "";
    }

    /**
     * 从指定的服务URL获取IP地址
     *
     * @param serviceUrl 外部服务的URL
     * @return 获取到的IP地址字符串
     * @throws Exception 如果发生网络或IO错误
     */
    private static String fetchIpFromService(String serviceUrl) throws Exception {
        URL url = new URL(serviceUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);

        int status = connection.getResponseCode();
        if (status == HttpURLConnection.HTTP_OK) {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                return in.readLine();
            }
        } else {
            throw new RuntimeException("非成功响应代码: " + status);
        }
    }
}
