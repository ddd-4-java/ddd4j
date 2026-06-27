package io.ddd4j.extension.monitor.domain.common.vo;

import lombok.Data;

/**
 * Dingding msg
 *
 * @author Loong Wan
 * @公众号 PartMe.AI
 */
@Data
public class MsgVO {
    private String msgtype;
    private TextVO text;
    private AtVO at;
    private MarkDownVO markdown;
}