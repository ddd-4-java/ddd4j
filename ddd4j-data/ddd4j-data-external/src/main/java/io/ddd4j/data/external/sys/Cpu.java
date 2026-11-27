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

    /**
     * \u8fd4\u56deCPU \u603b\u4f7f\u7528\u7387\uff08\u767e\u5206\u6bd4\uff09\u3002
     * @return CPU \u603b\u4f7f\u7528\u7387\uff08\u767e\u5206\u6bd4\uff09
     */
    public double getTotal() {
        return ArithKit.round(ArithKit.mul(total, 100), 2);
    }

    /**
     * \u8fd4\u56deCPU \u7cfb\u7edf\u4f7f\u7528\u7387\uff08\u767e\u5206\u6bd4\uff09\u3002
     * @return CPU \u7cfb\u7edf\u4f7f\u7528\u7387\uff08\u767e\u5206\u6bd4\uff09
     */
    public double getSys() {
        return ArithKit.round(ArithKit.mul(sys / total, 100), 2);
    }

    /**
     * \u8fd4\u56deCPU \u7528\u6237\u4f7f\u7528\u7387\uff08\u767e\u5206\u6bd4\uff09\u3002
     * @return CPU \u7528\u6237\u4f7f\u7528\u7387\uff08\u767e\u5206\u6bd4\uff09
     */
    public double getUsed() {
        return ArithKit.round(ArithKit.mul(used / total, 100), 2);
    }

    /**
     * \u8fd4\u56deCPU \u7b49\u5f85\u7387\uff08\u767e\u5206\u6bd4\uff09\u3002
     * @return CPU \u7b49\u5f85\u7387\uff08\u767e\u5206\u6bd4\uff09
     */
    public double getWait() {
        return ArithKit.round(ArithKit.mul(wait / total, 100), 2);
    }

    /**
     * \u8fd4\u56deCPU \u7a7a\u95f2\u7387\uff08\u767e\u5206\u6bd4\uff09\u3002
     * @return CPU \u7a7a\u95f2\u7387\uff08\u767e\u5206\u6bd4\uff09
     */
    public double getFree() {
        return ArithKit.round(ArithKit.mul(free / total, 100), 2);
    }

}
