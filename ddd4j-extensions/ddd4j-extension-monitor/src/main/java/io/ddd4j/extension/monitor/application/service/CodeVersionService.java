package io.ddd4j.extension.monitor.application.service;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.extension.monitor.domain.common.vo.CodeVersionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Properties;

@Slf4j
@Service
/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class CodeVersionService implements ApplicationListener<ApplicationEvent> {
    public static CodeVersionVO CODE_VERSION = null;
    @Autowired
    Sender sender;
    @Value("${spring.application.name:UNKNOWN}")
    private String appName;

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (!(event instanceof ContextRefreshedEvent refreshed) || refreshed.getApplicationContext().getParent() != null) {
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
