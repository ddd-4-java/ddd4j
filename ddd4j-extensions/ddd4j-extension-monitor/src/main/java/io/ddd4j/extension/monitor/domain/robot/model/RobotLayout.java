package io.ddd4j.extension.monitor.domain.robot.model;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.LayoutBase;
import io.ddd4j.extension.monitor.application.service.CodeVersionService;
import io.ddd4j.extension.monitor.infras.config.BaseMonitorProperties;
import io.ddd4j.extension.monitor.infras.utils.IpUtils;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.spring.context.SpringContext;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 解析处理日志
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class RobotLayout extends LayoutBase<ILoggingEvent> {
    /**
     * 线程安全的日期格式化
     */
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT_THREAD_LOCAL = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS"));
    /**
     * 应用名称
     */
    private String app;
    /**
     * 服务器 IP 地址
     */
    private String ip = "";
    /**
     * 项目名称
     */
    private String project = "";
    /**
     * 需要打印的 MDC 信息列表
     */
    private List<String> mdcList = new ArrayList<>();
    /**
     * 异常堆栈转换器
     */
    private ThrowableProxyConverter throwableProxyConverter = new ThrowableProxyConverter();

    @Override
    public void start() {
        throwableProxyConverter.setOptionList(List.of("3"));
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
        if (Objects.nonNull(CodeVersionService.CODE_VERSION)) {
            this.markdownTextAppend(sb, "Commit", StrKit.format("{} @{}", CodeVersionService.CODE_VERSION.getCommitId().substring(CodeVersionService.CODE_VERSION.getCommitId().length() - 8), CodeVersionService.CODE_VERSION.getCommitUser()));
        }
        this.markdownTextAppend(sb, "Message", event.getFormattedMessage());
        this.mdcAppend(sb, event);
        if (Objects.nonNull(event.getThrowableProxy())) {
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
