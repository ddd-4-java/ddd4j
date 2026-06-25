package io.ddd4j.boot.monitor.domain.common.vo;

import lombok.Data;

import java.util.List;

/**
 * dingding at
 *
 * @author Jensen
 * @公众号 架构师修行录
 */
@Data
public class AtVO {

    private List<String> atMobiles;
    private Boolean isAtAll = false;
}