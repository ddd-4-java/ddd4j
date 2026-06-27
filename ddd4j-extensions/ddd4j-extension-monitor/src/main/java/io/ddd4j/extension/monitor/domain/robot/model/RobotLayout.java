package io.ddd4j.extension.monitor.domain.robot.model;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.LayoutBase;
import io.ddd4j.spring.context.SpringContext;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.extension.monitor.application.service.CodeVersionService;
import io.ddd4j.extension.monitor.infras.config.BaseMonitorProperties;
import io.ddd4j.extension.monitor.infras.utils.IpUtils;
import com.google.common.collect.Lists;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

/**
 * 解析处理日志
 *
 * @author Jensen公众号：架构师修行录
 */
@Data
public class RobotLayout extends LayoutBase<ILoggingEvent> {
    // 应用名称
    private String app;
    // ip 地址
    private String ip = "";
    private String project = "";
    // 需要打印的mdc的信息
    private List<String> mdcList = Lists.newArrayList();
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT_THREAD_LOCAL = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS"));
    private ThrowableProxyConverter throwableProxyConverter = new ThrowableProxyConverter();

    @Override
    public void start() {
        throwableProxyConverter.setOptionList(Lists.newArrayList("3"));
        throwableProxyConverter.start();
        ip = IpUtils.getLocalAddress();
        super.start();
    }

    @Override
    public String doLayout(ILoggingEvent event) {
        if (!isStarted()) {
            return CoreConstants.EMPTY_STRING;
        }
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(this.getPresentationHeader())) {
            sb.append("## ").append(this.getPresentationHeader()).append("\n");
        }
        this.markdownTextAppend(sb, "App", app);
        this.markdownTextAppend(sb, "Project", project);
        this.markdownTextAppend(sb, "IP", ip);
        if (CodeVersionService.CODE_VERSION != null) {
            this.markdownTextAppend(sb, "Commit", StrKit.format("{} @{}", CodeVersionService.CODE_VERSION.getCommitId().substring(CodeVersionService.CODE_VERSION.getCommitId().length() - 8), CodeVersionService.CODE_VERSION.getCommitUser()));
        }
        this.markdownTextAppend(sb, "Message", event.getFormattedMessage());
        this.mdcAppend(sb, event);
        if (event.getThrowableProxy() != null) {
            String stackTrace = throwableProxyConverter.convert(event);
            BaseMonitorProperties baseMonitorProperties = SpringContext.getBean(BaseMonitorProperties.class);
            stackTrace = stackTrace.length() > baseMonitorProperties.getLog().getConfig().getMaxLength() ? stackTrace.substring(0, baseMonitorProperties.getLog().getConfig().getMaxLength() - 1) + "..." : stackTrace;
            this.markdownTextAppend(sb, "StackTraces", stackTrace);
        }
        return sb.toString();
    }

    // md 格式
    private void markdownTextAppend(StringBuilder sb, String key, String value) {
        if (StringUtils.hasText(value)) {
            sb.append("**").append(key).append(":** ").append(value).append("\n");
        }
    }

    // mdc 处理
    private void mdcAppend(StringBuilder sb, ILoggingEvent event) {
        Map<String, String> mdcPropertyMap = event.getMDCPropertyMap();
        for (Map.Entry<String, String> entry : mdcPropertyMap.entrySet()) {
            if (StringUtils.hasText(entry.getKey()) && StringUtils.hasText(entry.getValue()) && mdcList.contains(entry.getKey())) {
                this.markdownTextAppend(sb, entry.getKey(), entry.getValue());
            }
        }
    }
}
