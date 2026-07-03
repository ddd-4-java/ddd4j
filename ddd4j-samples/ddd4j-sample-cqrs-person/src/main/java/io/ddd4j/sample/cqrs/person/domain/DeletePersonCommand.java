package io.ddd4j.sample.cqrs.person.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 删除人员命令。
 *
 * <p>封装删除人员所需的请求参数，通过 {@link PersonCommandService} 处理。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeletePersonCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 人员 ID
     */
    private String personId;
}
