package io.ddd4j.data.external.sys;

import io.ddd4j.kit.lang.ArithKit;
import lombok.Data;

/**
 * CPU相关信息
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class Cpu {
    /**
     * 核心数
     */
    private int cpuNum;

    /**
     * CPU总的使用率
     */
    private double total;

    /**
     * CPU系统使用率
     */
    private double sys;

    /**
     * CPU用户使用率
     */
    private double used;

    /**
     * CPU当前等待率
     */
    private double wait;

    /**
     * CPU当前空闲率
     */
    private double free;

    public double getTotal() {
        return ArithKit.round(ArithKit.mul(total, 100), 2);
    }

    public double getSys() {
        return ArithKit.round(ArithKit.mul(sys / total, 100), 2);
    }

    public double getUsed() {
        return ArithKit.round(ArithKit.mul(used / total, 100), 2);
    }

    public double getWait() {
        return ArithKit.round(ArithKit.mul(wait / total, 100), 2);
    }

    public double getFree() {
        return ArithKit.round(ArithKit.mul(free / total, 100), 2);
    }

}
