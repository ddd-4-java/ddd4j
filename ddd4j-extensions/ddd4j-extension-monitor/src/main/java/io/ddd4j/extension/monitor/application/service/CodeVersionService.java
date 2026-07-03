package io.ddd4j.extension.monitor.application.service;

import io.ddd4j.extension.monitor.domain.common.vo.CodeVersionVO;
import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Objects;
import java.util.Properties;

/**
 * 代码版本服务
 *
 * <p>监听应用启动事件，读取 git.properties 中的代码版本信息，
 * 并在应用启动成功后通过告警机器人发送版本通知。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@Service
public class CodeVersionService implements ApplicationListener<ApplicationEvent> {
    /**
     * 当前代码版本信息，应用启动时初始化
     */
    public static CodeVersionVO CODE_VERSION = null;
    @Autowired
    Sender sender;
    /**
     * 应用名称，从配置文件中读取
     */
    @Value("${spring.application.name:UNKNOWN}")
    private String appName;

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (!(event instanceof ContextRefreshedEvent refreshed) || Objects.nonNull(refreshed.getApplicationContext().getParent())) {
            return;
        }
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
        if (StringUtils.hasText(value)) {
            sb.append("**").append(key).append(":** ").append(value).append("\n");
        }
    }
}
