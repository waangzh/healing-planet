package com.green.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final PathMatcher pathMatcher = new AntPathMatcher();

    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        try {
            if (isProtectedUrl(request)) {
                if (!request.getMethod().equalsIgnoreCase("OPTIONS")) {

                    HttpServletRequest wrappedRequest = JwtUtil.validateTokenAndAddUserIdToHeader(request);

                    // 从包装后的请求里获取userName
                    String username = wrappedRequest.getHeader(JwtUtil.USER_NAME);
                    System.out.println(JwtUtil.USER_NAME+"从请求头取的用户名：" + username);

                    if (username == null) {
                        throw new JwtUtil.TokenValidationException("Token中无用户名信息");
                    }

                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    if (userDetails == null) {
                        throw new JwtUtil.TokenValidationException("用户不存在");
                    }

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    filterChain.doFilter(wrappedRequest, response);
                    return;
                }
            }
        } catch (JwtUtil.TokenValidationException e) {
            log.warn("Token验证失败: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
            return;
        } catch (Exception e) {
            log.error("过滤器异常", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "服务器错误");
            return;
        }

        // 对于非受保护路径，直接继续
        filterChain.doFilter(request, response);
    }


    private boolean isProtectedUrl(HttpServletRequest request) {
        List<String> protectedPaths = new ArrayList<>();
        protectedPaths.add("/ums/user/info");
        protectedPaths.add("/ums/user/update");
        protectedPaths.add("/post/create");
        protectedPaths.add("/post/update");
        protectedPaths.add("/post/delete/*");
        protectedPaths.add("/comment/add_comment");
        protectedPaths.add("/relationship/subscribe/*");
        protectedPaths.add("/relationship/unsubscribe/*");
        protectedPaths.add("/relationship/validate/*");
        protectedPaths.add("/like/post/*");
        protectedPaths.add("/like/*");
        protectedPaths.add("/like/validate/*");
        protectedPaths.add("/notification");
        protectedPaths.add("/notification/*");
        protectedPaths.add("/userBindDevice");
        protectedPaths.add("/recommend/**");
        protectedPaths.add("/writePost");
        protectedPaths.add("/postLog");
        protectedPaths.add("/post/postLog");
        protectedPaths.add("/collect/*");
        protectedPaths.add("/common/chat/stream");
        protectedPaths.add("/common/upload");
        protectedPaths.add("/admin/**");
        for (String path : protectedPaths) {
            if (pathMatcher.match(path, request.getServletPath())) {
                return true;
            }
        }
        return false;
    }
}
