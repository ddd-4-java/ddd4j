/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.web.webmvc.config;

import cn.hutool.core.date.SystemClock;
import lombok.Data;

/**
 * 序列号配置属性（雪花算法）。
 * <p>配置雪花算法 ID 生成器的相关参数，如是否使用系统时钟、允许时间回拨量等。</p>
 */
@Data
public class SequenceProperties {

    public static final String PREFIX = "sequence";

    /** 是否使用 {@link SystemClock} 获取当前时间戳 */
    private boolean useSystemClock;
    /** 允许时间回拨的毫秒量，建议 5ms */
    private Long timeOffset = 5L;
    /** 限定一个随机上限，在不同毫秒下生成序号时给定一个随机数，避免偶数问题，0 表示无随机，上限不包括值本身 */
    private Long randomSequenceLimit;

}
