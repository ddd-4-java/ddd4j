package io.ddd4j.web.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RequestInfo {
    private String url;
    private Object params;
    private Map<String, Object> context;
}