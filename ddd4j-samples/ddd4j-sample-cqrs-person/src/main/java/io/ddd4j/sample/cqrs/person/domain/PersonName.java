package io.ddd4j.sample.cqrs.person.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.kit.lang.StrKit;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;

/**
 * 人员姓名值对象。
 *
 * <p>封装人员姓名，确保姓名不为空且合法。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Getter
@ToString
@EqualsAndHashCode
public class PersonName implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 姓名值
     */
    private final String value;

    /**
     * 构造函数。
     *
     * @param value 姓名字符串（不能为空）
     * @throws IllegalArgumentException 如果 value 为空或空白
     */
    @JsonCreator
    public PersonName(@JsonProperty("value") String value) {
        if (StrKit.isBlank(value)) {
            throw new IllegalArgumentException("person name must not be blank");
        }
        this.value = value;
    }
}
