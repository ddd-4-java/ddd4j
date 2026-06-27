package io.ddd4j.extension.monitor.domain.common.vo;

import lombok.Data;

import java.util.List;

/**
 * dingding at
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class AtVO {

    private List<String> atMobiles;
    private Boolean isAtAll = false;
}