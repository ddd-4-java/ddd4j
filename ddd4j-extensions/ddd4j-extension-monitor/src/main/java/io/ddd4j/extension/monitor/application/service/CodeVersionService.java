package io.ddd4j.extension.monitor.application.service;

import io.ddd4j.extension.monitor.domain.common.vo.CodeVersionVO;
import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Objects;
import java.util.Properties;

/**
 * 代码版本服务
 *
 * <p>读取 git.properties 中的代码版本信息，并在应用启动成功后通过告警机器人发送版本通知。
 *
 * <p>本类为纯 Java 实现，不再依赖 Spring 容器；上层框架在应用就绪后调用 {@link #init()} 即可触发版本通知。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class CodeVersionService {
    /**
     * 当前代码版本信息，应用启动时初始化
     */
    public static CodeVersionVO CODE_VERSION = null;

    /**
     * 消息发送器
     */
    private final Sender sender;
    /**
     * 应用名称，由构造方法传入
     */
    private final String appName;

    public CodeVersionService(Sender sender, String appName) {
        this.sender = sender;
        this.appName = appName;
    }

    /**
     * 初始化：读取代码版本信息并发送启动通知。
     *
     * <p>由上层框架在应用就绪后调用，等价于原先监听 {@code ContextRefreshedEvent} 的行为。
     */
    public void init() {
        try {
            Properties p = new Properties();
            p.load(this.getClass().getClassLoader().getResourceAsStream("git.properties"));
            CODE_VERSION = new CodeVersionVO(p.getProperty("git.branch"), p.getProperty("git.build.time"), p.getProperty("git.build.version"),
                    p.getProperty("git.commit.id"), p.getProperty("git.commit.message.full"), p.getProperty("git.commit.user.name"), p.getProperty("git.commit.time"));
            log.info("代码版本 => {}", CODE_VERSION);
            String projectStackTrace = null;
            StringBuilder sb = new StringBuilder();
            sb.append(StrKit.format("应用 **{}** 启动成功！", appName)).append("\n");
            this.markdownTextAppend(sb, "提交ID", CODE_VERSION.getCommitId());
            this.markdownTextAppend(sb, "提交用户", CODE_VERSION.getCommitUser());
            this.markdownTextAppend(sb, "提交信息", CODE_VERSION.getCommitMessage());
            this.markdownTextAppend(sb, "提交时间", CODE_VERSION.getCommitTime());
            if (Objects.nonNull(projectStackTrace)) {
                this.markdownTextAppend(sb, "StackTraces", projectStackTrace);
            }
            sender.send(sb.toString());
        } catch (IOException ignore) {
        }
    }

    private void markdownTextAppend(StringBuilder sb, String key, String value) {
        if (StrKit.isNotBlank(value)) {
            sb.append("**").append(key).append(":** ").append(value).append("\n");
        }
    }
}
