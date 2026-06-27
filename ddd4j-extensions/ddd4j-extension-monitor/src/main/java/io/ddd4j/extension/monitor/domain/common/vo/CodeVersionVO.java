package io.ddd4j.extension.monitor.domain.common.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class CodeVersionVO {
    private String branch;
    private String buildTime;
    private String buildVersion;
    private String commitId;
    private String commitMessage;
    private String commitUser;
    private String commitTime;
}
