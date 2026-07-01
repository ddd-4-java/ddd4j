package io.ddd4j.sample.cqrs.person.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.kit.lang.StrKit;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.util.UUID;

@Getter
@ToString
@EqualsAndHashCode
public class PersonId implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String value;

    @JsonCreator
    public PersonId(@JsonProperty("value") String value) {
        if (StrKit.isBlank(value)) {
            throw new IllegalArgumentException("person id must not be blank");
        }
        this.value = value;
    }

    public static PersonId newId() {
        return new PersonId(UUID.randomUUID().toString());
    }
}
