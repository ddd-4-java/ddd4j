package io.ddd4j.web.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 请求信息封装类。
 * <p>用于承载 HTTP 请求的 URL、参数和上下文数据，统一请求信息的传递结构。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RequestInfo {
    /** 请求 URL */
    private String url;
    /** 请求参数 */
    private Object params;
    /** 上下文数据 */
    private Map<String, Object> context;
}