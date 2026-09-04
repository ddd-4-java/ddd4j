package io.ddd4j.extension.monitor.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码版本信息（从 {@code git.properties} 读取）。was {@code CodeVersionVO}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeVersion {
    /**
     * Git 分支名称
     */
    private String branch;
    /**
     * 构建时间
     */
    private String buildTime;
    /**
     * 构建版本号
     */
    private String buildVersion;
    /**
     * Git 提交 ID
     */
    private String commitId;
    /**
     * Git 提交信息
     */
    private String commitMessage;
    /**
     * Git 提交用户
     */
    private String commitUser;
    /**
     * Git 提交时间
     */
    private String commitTime;
}
