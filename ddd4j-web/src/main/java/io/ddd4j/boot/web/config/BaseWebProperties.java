package io.ddd4j.boot.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "base-web")
public class BaseWebProperties {
    private Log log = new Log();
    private Mvc mvc = new Mvc();
    private Auth auth = new Auth();
    private Ws ws = new Ws();

    @Data
    public static class Log {
        // 日志拦截包含路径
        private String includes = "/**";
        // 日志拦截不包含路径
        private String excludes = "/error";
    }

    @Data
    public static class Mvc {
        // 启用R响应
        private Boolean enableRResponse = true;
    }

    @Data
    public static class Auth {
        // Bearer访问令牌列表
        private List<String> bearerTokens = new ArrayList<>();
    }

    @Data
    public static class Ws {
        // 断线重连时间，单位：分钟
        private Integer reconnectTime = 5;
    }

}