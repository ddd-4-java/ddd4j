package io.ddd4j.sample.cqrs.person.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 人员列表条目（读模型）。
 *
 * <p>用于人员列表视图的投影数据，包含人员的基本显示信息。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersonListEntry implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 人员 ID
     */
    private String personId;

    /**
     * 人员姓名
     */
    private String name;
}
