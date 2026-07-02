package io.ddd4j.core.domain.contract;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * 分页数据对象
 * 实现集合接口，集合操作的是records对象
 *
 * @param <T>
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Page<T> implements Iterable<T> {
    // 列表数据
    private List<T> records;
    // 总记录数
    private long total;
    // 回写当前页
    private long current = 1L;
    // 回写每页大小
    private long size = 10L;
    // 扩展字段
    private Map<String, Object> extras;

    public Page(long current, long size) {
        this.current = current;
        this.size = size;
        this.total = 0L;
        this.records = new ArrayList<>();
    }

    public static <T> Page<T> succeed(List<T> records, long total, long current, long size) {
        return new Page<>(records, total, current, size, new HashMap<>());
    }

    public static <T> Page<T> empty() {
        return new Page<>(new ArrayList<>(), 0L, 0L, 0L, Collections.emptyMap());
    }

    @Override
    public Iterator<T> iterator() {
        return Objects.nonNull(this.records) && !this.records.isEmpty() ? this.records.iterator() : null;
    }

    @Override
    public Spliterator<T> spliterator() {
        return Objects.nonNull(this.records) ? this.records.spliterator() : null;
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        if (Objects.nonNull(this.records)) {
            this.records.forEach(action);
        }
    }

    @JsonIgnore
    public boolean isEmpty() {
        return Objects.isNull(this.records) || this.records.isEmpty();
    }

    public boolean contains(Object o) {
        return Objects.nonNull(o) && Objects.nonNull(this.records) && this.records.contains(o);
    }

    public boolean add(T t) {
        return Objects.nonNull(this.records) && this.records.add(t);
    }

    public boolean remove(Object o) {
        return Objects.nonNull(this.records) && this.records.remove(o);
    }

    public boolean containsAll(Collection<?> c) {
        return Objects.nonNull(this.records) && this.records.containsAll(c);
    }

    public boolean addAll(Collection<? extends T> c) {
        return Objects.nonNull(this.records) && this.records.addAll(c);
    }

    public boolean removeAll(Collection<?> c) {
        return Objects.nonNull(this.records) && this.records.removeAll(c);
    }

    public boolean removeIf(Predicate<? super T> filter) {
        return Objects.nonNull(this.records) && this.records.removeIf(filter);
    }

    public boolean retainAll(Collection<?> c) {
        return Objects.nonNull(this.records) && this.records.retainAll(c);
    }

    public Stream<T> stream() {
        return Objects.nonNull(this.records) ? this.records.stream() : new ArrayList<T>().stream();
    }

    public Page<T> peek(Consumer<? super T> action) {
        if (Objects.nonNull(this.records)) {
            this.records.forEach(action);
        }
        return this;
    }

    public Page<T> setRecords(List<T> records) {
        this.records = records;
        return this;
    }

    public Page<T> setTotal(long total) {
        this.total = total;
        return this;
    }

    public Page<T> setSize(long size) {
        this.size = size;
        return this;
    }

    public Page<T> setCurrent(long current) {
        this.current = current;
        return this;
    }

    public Map<String, Object> extras() {
        if (Objects.isNull(extras)) {
            extras = new HashMap<>();
        }
        return extras;
    }
}