package io.ddd4j.core.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * \u5206\u9875\u6570\u636e\u5bf9\u8c61
 *   \u5b9e\u73b0\u96c6\u5408\u63a5\u53e3\uff0c\u96c6\u5408\u64cd\u4f5c\u7684\u662frecords\u5bf9\u8c61
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @param <T> \u7531 Page \u5904\u7406\u7684\u6cdb\u578b\u7c7b\u578b
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Page<T> implements Iterable<T> {
    // 列表数据
    /** \u56de\u5199\u5f53\u524d\u9875\u6570\u636e\u5217\u8868 */
    private List<T> records;
    // 总记录数
    /** \u56de\u5199\u603b\u8bb0\u5f55\u6570 */
    private long total;
    // 回写当前页
    /** \u56de\u5199\u5f53\u524d\u9875\u7801\uff0c\u9ed8\u8ba4 1 \u4ee3\u8868\u7b2c\u4e00\u9875 */
    private long current = 1L;
    // 回写每页大小
    /** \u56de\u5199\u6bcf\u9875\u5927\u5c0f\uff0c\u9ed8\u8ba4 10 \u6761\u6bcf\u9875 */
    private long size = 10L;
    // 扩展字段
    /** \u6269\u5c55\u5b57\u6bb5 */
    private Map<String, Object> extras;

    /**
     * 构造分页对象（仅指定页码和每页大小）。
     *
     * @param current 当前页码
     * @param size    每页大小
     */
    public Page(long current, long size) {
        this.current = current;
        this.size = size;
        this.total = 0L;
        this.records = new ArrayList<>();
    }

    /**
     * 创建成功的分页结果。
     *
     * @param records 当前页数据列表
     * @param total   总记录数
     * @param current 当前页码
     * @param size    每页大小
     * @param <T>     数据类型
     * @return 分页对象
     */
    public static <T> Page<T> succeed(List<T> records, long total, long current, long size) {
        return new Page<>(records, total, current, size, new HashMap<>());
    }

    /**
     * 创建空分页结果。
     *
     * @param <T> 数据类型
     * @return 空分页对象
     */
    public static <T> Page<T> empty() {
        return new Page<>(new ArrayList<>(), 0L, 0L, 0L, Collections.emptyMap());
    }

    /**
     * 通过<code>this.records.isEmpty()</code>、<code>this.records.iterator()</code>处理当前调用。
     *
     * @return 实际返回<code>Objects.nonNull(this.records) &amp;&amp; !this.records.isEmpty() ? this.records.iterator() : null</code>
     */
    @Override
    public Iterator<T> iterator() {
        return Objects.nonNull(this.records) && !this.records.isEmpty() ? this.records.iterator() : null;
    }

    /**
     * \u5bf9\u5f53\u524d\u9875\u8bb0\u5f55\u63d0\u4f9b\u62c6\u5206\u8fed\u4ee3\u5668\u3002
     * @return \u8bb0\u5f55\u5217\u8868\u7684\u62c6\u5206\u8fed\u4ee3\u5668\uff0c\u672a\u8bbe\u7f6e\u8bb0\u5f55\u65f6\u4e3a\u7a7a\u8fed\u4ee3\u5668
     */
    @Override
    public Spliterator<T> spliterator() {
        return Objects.nonNull(this.records) ? this.records.spliterator() : null;
    }

    /**
     * \u5bf9\u5f53\u524d\u9875\u8bb0\u5f55\u6309\u5217\u8868\u987a\u5e8f\u6267\u884c\u7ed9\u5b9a\u64cd\u4f5c\u3002
     * @param action \u63a5\u6536\u6bcf\u6761\u8bb0\u5f55\u7684\u64cd\u4f5c
     */
    @Override
    public void forEach(Consumer<? super T> action) {
        if (Objects.nonNull(this.records)) {
            this.records.forEach(action);
        }
    }

    /**
     * 判断当前页是否有数据。
     *
     * @return true 表示当前页无数据
     */
    @JsonIgnore
    public boolean isEmpty() {
        return Objects.isNull(this.records) || this.records.isEmpty();
    }

    /**
     * 判断当前页是否包含指定元素。
     *
     * @param o 待检查元素
     * @return true 表示包含
     */
    public boolean contains(Object o) {
        return Objects.nonNull(o) && Objects.nonNull(this.records) && this.records.contains(o);
    }

    /**
     * 向当前页添加元素。
     *
     * @param t 待添加元素
     * @return true 表示添加成功
     */
    public boolean add(T t) {
        return Objects.nonNull(this.records) && this.records.add(t);
    }

    /**
     * 从当前页移除指定元素。
     *
     * @param o 待移除元素
     * @return true 表示移除成功
     */
    public boolean remove(Object o) {
        return Objects.nonNull(this.records) && this.records.remove(o);
    }

    /**
     * \u5224\u65ad\u5f53\u524d\u9875\u662f\u5426\u5305\u542b\u6307\u5b9a\u96c6\u5408\u4e2d\u7684\u6240\u6709\u5143\u7d20\u3002
     *
     * @param c \u5f85\u68c0\u67e5\u96c6\u5408
     * @return \u5168\u90e8\u5143\u7d20\u90fd\u5728\u5f53\u524d\u9875\u65f6\u4e3a true
     */
    public boolean containsAll(Collection<?> c) {
        return Objects.nonNull(this.records) && this.records.containsAll(c);
    }

    /**
     * \u5411\u5f53\u524d\u9875\u6dfb\u52a0\u6307\u5b9a\u96c6\u5408\u4e2d\u7684\u6240\u6709\u5143\u7d20\u3002
     *
     * @param c \u5f85\u6dfb\u52a0\u96c6\u5408
     * @return \u5f53\u524d\u9875\u8bb0\u5f55\u53d1\u751f\u53d8\u5316\u65f6\u4e3a true
     */
    public boolean addAll(Collection<? extends T> c) {
        return Objects.nonNull(this.records) && this.records.addAll(c);
    }

    /**
     * \u4ece\u5f53\u524d\u9875\u79fb\u9664\u6307\u5b9a\u96c6\u5408\u4e2d\u7684\u6240\u6709\u5143\u7d20\u3002
     *
     * @param c \u5f85\u79fb\u9664\u96c6\u5408
     * @return \u5f53\u524d\u9875\u8bb0\u5f55\u53d1\u751f\u53d8\u5316\u65f6\u4e3a true
     */
    public boolean removeAll(Collection<?> c) {
        return Objects.nonNull(this.records) && this.records.removeAll(c);
    }

    /**
     * \u6309\u6761\u4ef6\u79fb\u9664\u5f53\u524d\u9875\u4e2d\u7684\u5143\u7d20\u3002
     *
     * @param filter \u5143\u7d20\u7b5b\u9009\u6761\u4ef6
     * @return \u5f53\u524d\u9875\u8bb0\u5f55\u53d1\u751f\u53d8\u5316\u65f6\u4e3a true
     */
    public boolean removeIf(Predicate<? super T> filter) {
        return Objects.nonNull(this.records) && this.records.removeIf(filter);
    }

    /**
     * \u4ec5\u4fdd\u7559\u5f53\u524d\u9875\u4e2d\u5305\u542b\u5728\u6307\u5b9a\u96c6\u5408\u4e2d\u7684\u5143\u7d20\u3002
     *
     * @param c \u8981\u4fdd\u7559\u7684\u5143\u7d20\u96c6\u5408
     * @return \u5f53\u524d\u9875\u8bb0\u5f55\u53d1\u751f\u53d8\u5316\u65f6\u4e3a true
     */
    public boolean retainAll(Collection<?> c) {
        return Objects.nonNull(this.records) && this.records.retainAll(c);
    }

    /**
     * \u83b7\u53d6\u5f53\u524d\u9875\u6570\u636e\u7684\u6d41\u3002
     *
     * @return \u5f53\u524d\u9875\u6570\u636e\u7684\u6d41\uff1b\u8bb0\u5f55\u4e3a\u7a7a\u65f6\u4e3a\u7a7a\u6d41
     */
    public Stream<T> stream() {
        return Objects.nonNull(this.records) ? this.records.stream() : new ArrayList<T>().stream();
    }

    /**
     * \u5bf9\u5f53\u524d\u9875\u5404\u6761\u8bb0\u5f55\u6267\u884c\u56de\u8c03\uff0c\u5e76\u8fd4\u56de\u5f53\u524d\u9875\u4f9b\u94fe\u5f0f\u8c03\u7528\u3002
     * @param action \u8bb0\u5f55\u5904\u7406\u56de\u8c03
     * @return \u5f53\u524d\u9875\u5bf9\u8c61
     */
    public Page<T> peek(Consumer<? super T> action) {
        if (Objects.nonNull(this.records)) {
            this.records.forEach(action);
        }
        return this;
    }

    /**
     * \u8bbe\u7f6e\u5f53\u524d\u9875\u8bb0\u5f55\u3002
     * @param records \u5f53\u524d\u9875\u8bb0\u5f55
     * @return \u5f53\u524d\u9875\u5bf9\u8c61
     */
    public Page<T> setRecords(List<T> records) {
        this.records = records;
        return this;
    }

    /**
     * \u8bbe\u7f6e\u603b\u8bb0\u5f55\u6570\u3002
     * @param total \u603b\u8bb0\u5f55\u6570
     * @return \u5f53\u524d\u9875\u5bf9\u8c61
     */
    public Page<T> setTotal(long total) {
        this.total = total;
        return this;
    }

    /**
     * \u8bbe\u7f6e\u6bcf\u9875\u5927\u5c0f\u3002
     * @param size \u6bcf\u9875\u5927\u5c0f
     * @return \u5f53\u524d\u9875\u5bf9\u8c61
     */
    public Page<T> setSize(long size) {
        this.size = size;
        return this;
    }

    /**
     * \u8bbe\u7f6e\u5f53\u524d\u9875\u7801\u3002
     * @param current \u5f53\u524d\u9875\u7801
     * @return \u5f53\u524d\u9875\u5bf9\u8c61
     */
    public Page<T> setCurrent(long current) {
        this.current = current;
        return this;
    }

    /**
     * \u83b7\u53d6\u6269\u5c55\u5b57\u6bb5\u6620\u5c04\uff0c\u9996\u6b21\u8bbf\u95ee\u65f6\u521b\u5efa\u7a7a\u6620\u5c04\u3002
     * @return \u53ef\u5199\u7684\u6269\u5c55\u5b57\u6bb5\u6620\u5c04
     */
    public Map<String, Object> extras() {
        if (Objects.isNull(extras)) {
            extras = new HashMap<>();
        }
        return extras;
    }
}