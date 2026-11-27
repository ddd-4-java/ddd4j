package io.ddd4j.core.health;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 多个 {@link ReadinessContributor} 的聚合结果。
 *
 * <p>任一关键依赖未就绪或检查异常时，报告均为未就绪。检查异常只转换为安全的状态原因，
 * 原始异常应由 Runtime 的日志或观测系统记录。
 */

public final class ReadinessReport {

    /** Java 序列化版本标识。 */
    private static final long serialVersionUID = 0L;

    /** \u62a5\u544a\u4e2d\u7684\u6240\u6709\u5fc5\u8981\u68c0\u67e5\u662f\u5426\u5df2\u901a\u8fc7\u3002 */
    private final boolean ready;

    /** \u6bcf\u4e2a\u5c31\u7eea\u8d21\u732e\u8005\u7684\u68c0\u67e5\u7ed3\u679c\u3002 */
    private final List<ReadinessResult> results;

    /**
     * 初始化 <code>ReadinessReport</code> 使用的 <code>ready</code>、<code>results</code>。
     *
     * @param ready \u662f\u5426\u53ef\u63a5\u6536\u6d41\u91cf
     * @param results \u6bcf\u4e2a\u5df2\u6267\u884c Contributor \u7684\u7ed3\u679c
     */

    @JsonCreator()
    public ReadinessReport(@JsonProperty("ready") boolean ready, @JsonProperty("results") List<ReadinessResult> results) {
        results = List.copyOf(Objects.requireNonNullElse(results, List.of()));
        this.ready = ready;
        this.results = results;
    }

    /**
     * 执行并汇总贡献者。
     *
     * @param contributors 依赖检查器集合
     * @return 聚合就绪报告
     */
    public static ReadinessReport check(Collection<? extends ReadinessContributor> contributors) {
        List<ReadinessResult> results = Objects.requireNonNullElse(contributors, List.<ReadinessContributor>of()).stream().filter(Objects::nonNull).map(ReadinessReport::checkContributor).toList();
        return new ReadinessReport(results.stream().allMatch(ReadinessResult::ready), results);
    }

    /**
     * \u6267\u884c\u7ec4\u4ef6\u5c31\u7eea\u68c0\u67e5\uff0c\u5e76\u628a\u68c0\u67e5\u5f02\u5e38\u6216\u7a7a\u7ed3\u679c\u8f6c\u6362\u6210\u5931\u8d25\u7ed3\u679c\u3002
     * @param contributor \u53c2\u52a0\u5c31\u7eea\u72b6\u6001\u6c47\u603b\u7684\u7ec4\u4ef6
     * @return \u7ec4\u4ef6\u7684\u68c0\u67e5\u7ed3\u679c\u6216\u5931\u8d25\u8bca\u65ad
     */
    private static ReadinessResult checkContributor(ReadinessContributor contributor) {
        try {
            ReadinessResult result = contributor.check();
            return Objects.requireNonNullElseGet(result, () -> ReadinessResult.unavailable(contributor.getClass().getSimpleName(), "empty result"));
        } catch (RuntimeException exception) {
            return ReadinessResult.unavailable(contributor.getClass().getSimpleName(), "check failed");
        }
    }

    /**
     * \u83b7\u53d6\u6574\u4f53\u5c31\u7eea\u72b6\u6001\uff0c\u4f9b\u4e1a\u52a1\u8c03\u7528\u53ca JSON \u5e8f\u5217\u5316\u4f7f\u7528\u3002
     * @return \u6574\u4f53\u5c31\u7eea\u65f6\u4e3a true
     */
    @JsonProperty("ready")
    public boolean ready() {
        return ready;
    }

    /**
     * \u83b7\u53d6\u5404\u4f9d\u8d56\u7684\u5c31\u7eea\u7ed3\u679c\uff0c\u4f9b\u4e1a\u52a1\u8c03\u7528\u53ca JSON \u5e8f\u5217\u5316\u4f7f\u7528\u3002
     * @return \u4e0d\u53ef\u53d8\u7684\u5c31\u7eea\u7ed3\u679c\u5217\u8868
     */
    @JsonProperty("results")
    public List<ReadinessResult> results() {
        return results;
    }

    /**
     * 根据<code>results</code>、<code>ready</code>比较对象的值是否相等。
     *
     * <p>只比较运行时类型相同的对象。</p>
     *
     * @param obj 用来比较或转换的输入对象
     * @return 对象满足此实现的相等条件时为真
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        ReadinessReport other = (ReadinessReport) obj;
        return this.ready == other.ready && Objects.equals(this.results, other.results);
    }

    /**
     * 计算与对象相等条件一致的哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Boolean.hashCode(ready);
        result = 31 * result + Objects.hashCode(results);
        return result;
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>ready</code>、<code>results</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "ReadinessReport[ready=" + ready + ", results=" + results + "]";
    }
}
