package io.ddd4j.boot.monitor.application.service;

import io.ddd4j.boot.core.utils.ExceptionKit;
import io.ddd4j.boot.kit.lang.StrKit;
import io.ddd4j.boot.monitor.domain.common.vo.CodeVersionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Properties;

@Slf4j
@Service
public class CodeVersionService implements ApplicationListener<ApplicationEvent> {
    public static CodeVersionVO CODE_VERSION = null;
    @Autowired
    Sender sender;
    @Value("${spring.application.name:UNKNOWN}")
    private String appName;

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (!(event instanceof ApplicationStartedEvent || event instanceof ApplicationFailedEvent)) {
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
            if (event instanceof ApplicationStartedEvent) {
                sb.append(StrKit.format("应用 **{}** 启动成功！", appName)).append("\n");
            } else {
                ApplicationFailedEvent failedEvent = (ApplicationFailedEvent) event;
                projectStackTrace = ExceptionKit.getProjectStackTraces(failedEvent.getException());
                sb.append(StrKit.format("应用 **{}** 启动失败！", appName)).append("\n");
            }
            this.markdownTextAppend(sb, "提交ID", CODE_VERSION.getCommitId());
            this.markdownTextAppend(sb, "提交用户", CODE_VERSION.getCommitUser());
            this.markdownTextAppend(sb, "提交信息", CODE_VERSION.getCommitMessage());
            this.markdownTextAppend(sb, "提交时间", CODE_VERSION.getCommitTime());
            if (projectStackTrace != null) {
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
