package io.ddd4j.sample.spring.shiro.config;

import io.ddd4j.kit.lang.StrKit;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Binds a Shiro Subject to each HTTP request using the ddd4j token header.
 */
@Component
public class ShiroSessionFilter extends OncePerRequestFilter {

    private static final String TOKEN_HEADER = "ddd4j-token";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader(TOKEN_HEADER);
        Subject.Builder builder = new Subject.Builder(SecurityUtils.getSecurityManager());
        if (StrKit.isNotBlank(token)) {
            builder.sessionId(token);
        }
        ThreadContext.bind(builder.buildSubject());
        try {
            filterChain.doFilter(request, response);
        } finally {
            ThreadContext.unbindSubject();
        }
    }
}
