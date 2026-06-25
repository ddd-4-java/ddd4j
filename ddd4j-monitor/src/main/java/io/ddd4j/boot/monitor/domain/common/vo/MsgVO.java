package io.ddd4j.boot.monitor.domain.common.vo;

import lombok.Data;

/**
 * Dingding msg
 *
 * @author Jensen
 * @公众号 架构师修行录
 */
@Data
public class MsgVO {
    private String msgtype;
    private TextVO text;
    private AtVO at;
    private MarkDownVO markdown;
}