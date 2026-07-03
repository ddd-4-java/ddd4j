package io.ddd4j.sample.cqrs.person.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.kit.lang.StrKit;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.util.UUID;

/**
 * 人员 ID 值对象。
 *
 * <p>封装人员标识，确保 ID 不为空且合法。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Getter
@ToString
@EqualsAndHashCode
public class PersonId implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 人员 ID 值
     */
    private final String value;

    /**
     * 构造函数。
     *
     * @param value 人员 ID 字符串（不能为空）
     * @throws IllegalArgumentException 如果 value 为空或空白
     */
    @JsonCreator
    public PersonId(@JsonProperty("value") String value) {
        if (StrKit.isBlank(value)) {
            throw new IllegalArgumentException("person id must not be blank");
        }
        this.value = value;
    }

    /**
     * 创建一个新的随机人员 ID。
     *
     * @return 新的人员 ID
     */
    public static PersonId newId() {
        return new PersonId(UUID.randomUUID().toString());
    }
}
