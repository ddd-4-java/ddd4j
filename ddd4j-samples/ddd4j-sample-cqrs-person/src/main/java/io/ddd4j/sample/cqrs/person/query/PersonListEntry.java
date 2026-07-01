package io.ddd4j.sample.cqrs.person.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersonListEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private String personId;

    private String name;
}
