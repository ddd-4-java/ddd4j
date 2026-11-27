/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.web.webflux.config;

import cn.hutool.core.date.SystemClock;
import lombok.Data;

/**
 * \u96ea\u82b1\u5e8f\u5217\u751f\u6210\u914d\u7f6e\u5c5e\u6027\u3002
 * @since 2.0.x
 */
@Data
public class SequenceProperties {

    /** \u914d\u7f6e\u9879\u524d\u7f00\uff08{@code @ConfigurationProperties} \u7ed1\u5b9a\u4f7f\u7528\uff09\u3002 */
    public static final String PREFIX = "sequence";

    /**
     * 是否使用{@link SystemClock} 获取当前时间戳
     */
    private boolean useSystemClock;
    /**
     * 允许时间回拨的毫秒量,建议5ms
     */
    private Long timeOffset = 5L;
    /**
     * 限定一个随机上限，在不同毫秒下生成序号时，给定一个随机数，避免偶数问题，0表示无随机，上限不包括值本身。
     */
    private Long randomSequenceLimit;


}
