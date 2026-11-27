package io.ddd4j.data.external.sys;

import io.ddd4j.kit.lang.ArithKit;
import lombok.Data;

/**
 * 內存相关信息
 *
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

    /**
     * \u8fd4\u56de\u603b\u5185\u5b58\uff08GB\uff09\u3002
     * @return \u603b\u5185\u5b58\uff08GB\uff09
     */
    public double getTotal() {
        return ArithKit.div(total, (1024 * 1024 * 1024), 2);
    }

    /**
     * \u8fd4\u56de\u5df2\u7528\u5185\u5b58\uff08GB\uff09\u3002
     * @return \u5df2\u7528\u5185\u5b58\uff08GB\uff09
     */
    public double getUsed() {
        return ArithKit.div(used, (1024 * 1024 * 1024), 2);
    }

    /**
     * \u8fd4\u56de\u7a7a\u95f2\u5185\u5b58\uff08GB\uff09\u3002
     * @return \u7a7a\u95f2\u5185\u5b58\uff08GB\uff09
     */
    public double getFree() {
        return ArithKit.div(free, (1024 * 1024 * 1024), 2);
    }

    /**
     * \u8fd4\u56de\u5185\u5b58\u4f7f\u7528\u7387\uff08\u767e\u5206\u6bd4\uff09\u3002
     * @return \u5185\u5b58\u4f7f\u7528\u7387\uff08\u767e\u5206\u6bd4\uff09
     */
    public double getUsage() {
        return ArithKit.mul(ArithKit.div(used, total, 4), 100);
    }
}
