package io.ddd4j.data.external.sys;

import hitool.core.lang3.time.DateFormats;
import hitool.core.lang3.time.LocalDateTimes;
import io.ddd4j.kit.lang.ArithKit;
import io.ddd4j.kit.lang.DateKit;
import lombok.Data;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;

/**
 * JVM相关信息
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class Jvm {
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

    public double getTotal() {
        return ArithKit.div(total, (1024 * 1024), 2);
    }

    public double getMax() {
        return ArithKit.div(max, (1024 * 1024), 2);
    }

    public double getFree() {
        return ArithKit.div(free, (1024 * 1024), 2);
    }

    public double getUsed() {
        return ArithKit.div(total - free, (1024 * 1024), 2);
    }

    public double getUsage() {
        return ArithKit.mul(ArithKit.div(total - free, total, 4), 100);
    }

    /**
     * 获取JDK名称
     */
    public String getName() {
        return ManagementFactory.getRuntimeMXBean().getVmName();
    }

    /**
     * JDK启动时间
     */
    public String getStartTime() {
        long time = ManagementFactory.getRuntimeMXBean().getStartTime();
        LocalDateTime date = DateKit.millsToLocalDateTime(time);
        return LocalDateTimes.format(date, DateFormats.YYYYMMDDHHMMSS);
    }

    /**
     * JDK运行时间
     */
    public String getRunTime() {
        long time = ManagementFactory.getRuntimeMXBean().getStartTime();
        LocalDateTime startDateTime = DateKit.millsToLocalDateTime(time);
        return DateKit.getDatePoor(LocalDateTime.now(), startDateTime);
    }

    /**
     * 运行参数
     */
    public String getInputArgs() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().toString();
    }
}
