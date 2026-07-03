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

    /**
     * @ 的手机号列表
     */
    private List<String> atMobiles;
    /**
     * 是否 @ 所有人
     */
    private Boolean isAtAll = false;
}