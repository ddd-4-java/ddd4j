package io.ddd4j.extension.excel.importer;

import java.util.ArrayList;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * 导入结果（不可变视图）。
 *
 * <p>封装一次 Excel 读取的全部产物：成功数据列表 + 错误行列表。
 * <b>错误永不抛出到调用方</b>，通过 {@link #hasErrors()} 与 {@link #getErrors()} 暴露。
 *
 * <p>参考社区共识（Yudao / 灯灯 / RuoYi）：导入是"尽力而为"过程，单行失败不阻断整体流程，
 * 由调用方决定是否回滚或部分入库。
 *
 * @param <T> 数据类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Getter
public final class ImportResult<T> {

    private final List<T> data;
    private final List<ImportError> errors;

    private ImportResult(List<T> data, List<ImportError> errors) {
        this.data = Collections.unmodifiableList(new ArrayList<>(data));
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }

    /**
     * 工厂方法。
     *
     * @param data   成功数据
     * @param errors 错误列表
     * @param <T>    数据类型
     * @return {@link ImportResult}
     */
    public static <T> ImportResult<T> of(List<T> data, List<ImportError> errors) {
        return new ImportResult<>(data, errors);
    }

    /**
     * 空结果。
     */
    public static <T> ImportResult<T> empty() {
        return new ImportResult<>(Collections.emptyList(), Collections.emptyList());
    }

    /**
     * 是否包含错误行。
     *
     * @return true 表示有错误
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * 总行数（成功 + 失败）。
     *
     * @return 处理过的行数
     */
    public int getTotalCount() {
        return data.size() + errors.size();
    }

    /**
     * 成功率（0.0 ~ 1.0）。
     *
     * @return 成功数 / 总数；总数为 0 时返回 0.0
     */
    public double successRate() {
        int total = getTotalCount();
        return total == 0 ? 0.0 : (double) data.size() / total;
    }
}
