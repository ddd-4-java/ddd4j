package io.ddd4j.boot.cmpt.external.sys;

import hitool.core.lang3.time.DateFormats;
import hitool.core.lang3.time.LocalDateTimes;
import io.ddd4j.boot.core.util.Arith;
import io.ddd4j.boot.core.util.DateUtils;
import lombok.Data;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;

/**
 * JVM相关信息
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
        return Arith.div(total, (1024 * 1024), 2);
    }

    public double getMax() {
        return Arith.div(max, (1024 * 1024), 2);
    }

    public double getFree() {
        return Arith.div(free, (1024 * 1024), 2);
    }

    public double getUsed() {
        return Arith.div(total - free, (1024 * 1024), 2);
    }

    public double getUsage() {
        return Arith.mul(Arith.div(total - free, total, 4), 100);
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
        LocalDateTime date = DateUtils.millsToLocalDateTime(time);
        return LocalDateTimes.format(date, DateFormats.YYYYMMDDHHMMSS);
    }

    /**
     * JDK运行时间
     */
    public String getRunTime() {
        long time = ManagementFactory.getRuntimeMXBean().getStartTime();
        LocalDateTime startDateTime = DateUtils.millsToLocalDateTime(time);
        return DateUtils.getDatePoor(LocalDateTime.now(), startDateTime);
    }

    /**
     * 运行参数
     */
    public String getInputArgs() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().toString();
    }
}
