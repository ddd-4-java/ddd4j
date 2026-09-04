package io.ddd4j.web.webmvc.config;

import lombok.Data;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 国际化 MessageSource 配置（对应 Boot {@code spring.messages.*}，Framework 层本地绑定）。
 */
@Data
public class MessageSourceConfigurationProperties {

    /** 资源 bundle 基名列表，默认 {@code messages} */
    private List<String> basename = new ArrayList<String>(Collections.singletonList("messages"));

    /** 默认编码 */
    private Charset encoding = StandardCharsets.UTF_8;

    /** 找不到 locale 时是否回退系统 locale */
    private boolean fallbackToSystemLocale = true;

    /** 缓存时长 */
    private Duration cacheDuration;

    /** 是否始终使用 MessageFormat */
    private boolean alwaysUseMessageFormat = false;

    /** 找不到 key 时是否直接使用 key 作为默认消息 */
    private boolean useCodeAsDefaultMessage = false;
}
