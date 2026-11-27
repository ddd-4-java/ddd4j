package io.ddd4j.data.external.sys;

import io.ddd4j.kit.lang.ArithKit;
import io.ddd4j.kit.lang.DateKit;
import lombok.Data;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * JVM相关信息
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class Jvm {
    /** \u683c\u5f0f\u5316 JVM \u542f\u52a8\u65f6\u95f4\u7684\u65e5\u671f\u65f6\u95f4\u683c\u5f0f\u5668\u3002 */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 当前JVM占用的内存总数(M)
     */
    private double total;

    /**
     * JVM最大可用内存总数(M)
     */
    private double max;

    /**
     * JVM空闲内存(M)
     */
    private double free;

    /**
     * JDK版本
     */
    private String version;

    /**
     * JDK路径
     */
    private String home;

    /**
     * \u8fd4\u56deJVM \u603b\u5185\u5b58\uff08MB\uff09\u3002
     * @return JVM \u603b\u5185\u5b58\uff08MB\uff09
     */
    public double getTotal() {
        return ArithKit.div(total, (1024 * 1024), 2);
    }

    /**
     * \u8fd4\u56deJVM \u6700\u5927\u5185\u5b58\uff08MB\uff09\u3002
     * @return JVM \u6700\u5927\u5185\u5b58\uff08MB\uff09
     */
    public double getMax() {
        return ArithKit.div(max, (1024 * 1024), 2);
    }

    /**
     * \u8fd4\u56deJVM \u7a7a\u95f2\u5185\u5b58\uff08MB\uff09\u3002
     * @return JVM \u7a7a\u95f2\u5185\u5b58\uff08MB\uff09
     */
    public double getFree() {
        return ArithKit.div(free, (1024 * 1024), 2);
    }

    /**
     * \u8fd4\u56deJVM \u5df2\u7528\u5185\u5b58\uff08MB\uff09\u3002
     * @return JVM \u5df2\u7528\u5185\u5b58\uff08MB\uff09
     */
    public double getUsed() {
        return ArithKit.div(total - free, (1024 * 1024), 2);
    }

    /**
     * \u8fd4\u56deJVM \u5185\u5b58\u4f7f\u7528\u7387\uff08\u767e\u5206\u6bd4\uff09\u3002
     * @return JVM \u5185\u5b58\u4f7f\u7528\u7387\uff08\u767e\u5206\u6bd4\uff09
     */
    public double getUsage() {
        return ArithKit.mul(ArithKit.div(total - free, total, 4), 100);
    }

    /**
     * \u83b7\u53d6JDK\u540d\u79f0
     *
     * @return JVM \u540d\u79f0
     */
    public String getName() {
        return ManagementFactory.getRuntimeMXBean().getVmName();
    }

    /**
     * JDK\u542f\u52a8\u65f6\u95f4
     *
     * @return JVM \u542f\u52a8\u65f6\u95f4
     */
    public String getStartTime() {
        long time = ManagementFactory.getRuntimeMXBean().getStartTime();
        LocalDateTime date = DateKit.millsToLocalDateTime(time);
        return date.format(DATE_TIME_FORMATTER);
    }

    /**
     * JDK\u8fd0\u884c\u65f6\u95f4
     *
     * @return JVM \u8fd0\u884c\u65f6\u95f4
     */
    public String getRunTime() {
        long time = ManagementFactory.getRuntimeMXBean().getStartTime();
        LocalDateTime startDateTime = DateKit.millsToLocalDateTime(time);
        return DateKit.getDatePoor(startDateTime, LocalDateTime.now());
    }

    /**
     * \u8fd0\u884c\u53c2\u6570
     *
     * @return JVM \u542f\u52a8\u53c2\u6570
     */
    public String getInputArgs() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().toString();
    }
}
