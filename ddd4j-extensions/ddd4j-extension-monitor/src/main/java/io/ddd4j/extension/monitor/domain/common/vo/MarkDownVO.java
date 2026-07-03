package io.ddd4j.extension.monitor.domain.common.vo;

import lombok.Data;

/**
 * dingding markdown
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class MarkDownVO {
    /**
     * Markdown 标题
     */
    private String title;
    /**
     * Markdown 文本内容
     */
    private String text;
    /**
     * Markdown 内容（备用字段）
     */
    private String content;
}