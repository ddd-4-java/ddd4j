package io.ddd4j.extension.monitor.domain.common.vo;

import lombok.Data;

/**
 * Dingding msg
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class MsgVO {
    private String msgtype;
    private TextVO text;
    private AtVO at;
    private MarkDownVO markdown;
}