package io.ddd4j.sample.auth.multilogin.web;

import io.ddd4j.cache.subject.InMemorySubject;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/**
 * Bearer Token 认证过滤器。
 *
 * <p>从请求头中提取 Bearer Token 并绑定到当前线程的 Subject 上下文，
 * 使得业务代码可通过 SubjectKit 获取当前登录用户信息。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Component
public class BearerTokenSubjectFilter extends OncePerRequestFilter {

    /**
     * Bearer Token 前缀
     */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 基于内存的 Subject
     */
    private final InMemorySubject subject;

    public BearerTokenSubjectFilter(InMemorySubject subject) {
        this.subject = subject;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (Objects.nonNull(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            subject.bind(authorization.substring(BEARER_PREFIX.length()));
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            subject.clear();
        }
    }
}
