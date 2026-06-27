package io.ddd4j.data.external.sys;

import io.ddd4j.kit.lang.ArithKit;
import lombok.Data;

/**
 * 內存相关信息
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class Mem {
    /**
     * 内存总量
     */
    private double total;

    /**
     * 已用内存
     */
    private double used;

    /**
     * 剩余内存
     */
    private double free;

    public double getTotal() {
        return ArithKit.div(total, (1024 * 1024 * 1024), 2);
    }

    public double getUsed() {
        return ArithKit.div(used, (1024 * 1024 * 1024), 2);
    }

    public double getFree() {
        return ArithKit.div(free, (1024 * 1024 * 1024), 2);
    }

    public double getUsage() {
        return ArithKit.mul(ArithKit.div(used, total, 4), 100);
    }
}
