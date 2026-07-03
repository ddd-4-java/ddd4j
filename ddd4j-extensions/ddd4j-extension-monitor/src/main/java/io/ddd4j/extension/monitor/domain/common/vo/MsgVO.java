package io.ddd4j.extension.monitor.domain.common.vo;

import lombok.Data;

/**
 * Dingding msg
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class MsgVO {
    /**
     * 消息类型（text/markdown）
     */
    private String msgtype;
    /**
     * 文本消息内容（当 msgtype 为 text 时使用）
     */
    private TextVO text;
    /**
     * @ 配置
     */
    private AtVO at;
    /**
     * Markdown 消息内容（当 msgtype 为 markdown 时使用）
     */
    private MarkDownVO markdown;
}