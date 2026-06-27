package io.ddd4j.extension.monitor.domain.common.vo;

import lombok.Data;

import java.util.List;

/**
 * dingding at
 *
 * @author Loong Wan
 */
@Data
public class AtVO {

    private List<String> atMobiles;
    private Boolean isAtAll = false;
}