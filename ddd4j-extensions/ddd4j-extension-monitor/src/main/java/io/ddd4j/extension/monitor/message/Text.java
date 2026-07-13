package io.ddd4j.extension.monitor.message;

import lombok.Data;

/**
 * 文本消息体。was {@code TextVO}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class Text {

    /**
     * 文本消息内容
     */
    private String content;
}
