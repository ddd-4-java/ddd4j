package io.ddd4j.extension.monitor.domain.common.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 代码版本 VO
 *
 * <p>封装从 git.properties 文件中读取的代码版本信息，包括分支、构建时间、提交信息等。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@AllArgsConstructor
public class CodeVersionVO {
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
     * Git 提交 ID（完整哈希值）
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
