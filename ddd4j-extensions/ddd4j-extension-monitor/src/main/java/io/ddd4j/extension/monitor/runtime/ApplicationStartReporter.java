package io.ddd4j.extension.monitor.runtime;

import io.ddd4j.extension.monitor.Sender;
import io.ddd4j.extension.monitor.message.CodeVersion;
import io.ddd4j.kit.lang.StrKit;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

/**
 * 应用启动通告。
 *
 * <p>读取 classpath 下的 {@code git.properties}（由 maven-git-commit-id 插件生成），
 * 在应用就绪后通过 {@link Sender} 向机器人发送 markdown 格式的启动通知。
 *
 * <p>纯 Java，不依赖任何 Web 框架；上层框架在应用就绪后调用 {@link #init()} 即可。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j(topic = "### DDD4j-MONITOR : ApplicationStartReporter ###")
public class ApplicationStartReporter {

    /**
     * 当前代码版本，应用启动时由 {@link #init()} 写入。
     */
    public static CodeVersion CODE_VERSION = null;

    private final Sender sender;
    private final String appName;

    public ApplicationStartReporter(Sender sender, String appName) {
        this.sender = sender;
        this.appName = appName;
    }

    /** @return 注入的应用名称（用于启动通告文案） */
    public String appName() {
        return appName;
    }

    /**
     * 读取 {@code git.properties} 并发送启动通知。
     *
     * <p>{@code git.properties} 缺失时不抛异常（仅日志记录），保证非 maven-git-commit-id 环境也能跑。
     */
    public void init() {
        Properties p = new Properties();
        try (InputStream in = this.getClass().getClassLoader().getResourceAsStream("git.properties")) {
            if (Objects.isNull(in)) {
                log.debug("未找到 git.properties，跳过启动通知");
                return;
            }
            p.load(in);
        } catch (IOException e) {
            log.debug("读取 git.properties 失败: {}", e.getMessage());
            return;
        }
        CODE_VERSION = new CodeVersion(
                p.getProperty("git.branch"),
                p.getProperty("git.build.time"),
                p.getProperty("git.build.version"),
                p.getProperty("git.commit.id"),
                p.getProperty("git.commit.message.full"),
                p.getProperty("git.commit.user.name"),
                p.getProperty("git.commit.time"));
        log.info("代码版本 => {}", CODE_VERSION);
        sender.send(buildNotice(CODE_VERSION));
    }

    private String buildNotice(CodeVersion v) {
        StringBuilder sb = new StringBuilder();
        sb.append(StrKit.format("应用 **{0}** 启动成功！", appName)).append("\n");
        markdownAppend(sb, "提交ID", v.getCommitId());
        markdownAppend(sb, "提交用户", v.getCommitUser());
        markdownAppend(sb, "提交信息", v.getCommitMessage());
        markdownAppend(sb, "提交时间", v.getCommitTime());
        return sb.toString();
    }

    private static void markdownAppend(StringBuilder sb, String key, String value) {
        if (Objects.nonNull(value) && StrKit.isNotBlank(value)) {
            sb.append("**").append(key).append(":** ").append(value).append("\n");
        }
    }
}
