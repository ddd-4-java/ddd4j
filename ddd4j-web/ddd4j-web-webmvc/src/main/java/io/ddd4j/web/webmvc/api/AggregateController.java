package io.ddd4j.web.webmvc.api;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.core.util.MappingKit;
import io.ddd4j.kit.lang.BeanKit;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Dynamic aggregate controller backed by the framework-neutral rich model SPI.
 *
 * <p>This keeps the old generic Web entry style, but no longer depends on the
 * removed {@code Model/BaseRepository} active-record registry.</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public interface AggregateController {

    /**
     * \u6309\u8bf7\u6c42\u4f53\u5206\u9875\u67e5\u8be2\u805a\u5408\u6839\u3002
     * @param model \u805a\u5408\u6839\u540d\u79f0\u6216\u6a21\u578b
     * @param body \u8bf7\u6c42\u4f53\u53c2\u6570
     * @return \u805a\u5408\u6839\u5206\u9875\u7ed3\u679c
     */
    @PostMapping("/{model}/page")
    default Page<? extends AggregateRoot<?>> postPage(@PathVariable("model") String model,
                                                      @RequestBody Map<String, Object> body) {
        return query(model, body).page();
    }

    /**
     * \u6309\u67e5\u8be2\u53c2\u6570\u5206\u9875\u67e5\u8be2\u805a\u5408\u6839\u3002
     * @param model \u805a\u5408\u6839\u540d\u79f0\u6216\u6a21\u578b
     * @param params \u8bf7\u6c42\u53c2\u6570
     * @return \u805a\u5408\u6839\u5206\u9875\u7ed3\u679c
     */
    @GetMapping("/{model}/page")
    default Page<? extends AggregateRoot<?>> getPage(@PathVariable("model") String model,
                                                     @RequestParam Map<String, Object> params) {
        return query(model, params).page();
    }

    /**
     * \u6309\u8bf7\u6c42\u4f53\u67e5\u8be2\u805a\u5408\u6839\u5217\u8868\u3002
     * @param model \u805a\u5408\u6839\u540d\u79f0\u6216\u6a21\u578b
     * @param body \u8bf7\u6c42\u4f53\u53c2\u6570
     * @return \u805a\u5408\u6839\u5217\u8868
     */
    @PostMapping("/{model}/list")
    default List<? extends AggregateRoot<?>> postList(@PathVariable("model") String model,
                                                      @RequestBody Map<String, Object> body) {
        return query(model, body).list();
    }

    /**
     * \u6309\u67e5\u8be2\u53c2\u6570\u67e5\u8be2\u805a\u5408\u6839\u5217\u8868\u3002
     * @param model \u805a\u5408\u6839\u540d\u79f0\u6216\u6a21\u578b
     * @param params \u8bf7\u6c42\u53c2\u6570
     * @return \u805a\u5408\u6839\u5217\u8868
     */
    @GetMapping("/{model}/list")
    default List<? extends AggregateRoot<?>> getList(@PathVariable("model") String model,
                                                     @RequestParam Map<String, Object> params) {
        return query(model, params).list();
    }

    /**
     * \u67e5\u8be2\u805a\u5408\u6839\u8be6\u60c5\u3002
     * @param model \u805a\u5408\u6839\u540d\u79f0\u6216\u6a21\u578b
     * @param params \u8bf7\u6c42\u53c2\u6570
     * @return \u805a\u5408\u6839\u8be6\u60c5
     */
    @GetMapping("/{model}/detail")
    default AggregateRoot<?> detail(@PathVariable("model") String model,
                                    @RequestParam Map<String, Object> params) {
        return query(model, params).first();
    }

    /**
     * \u67e5\u8be2\u805a\u5408\u6839\u8be6\u60c5\u3002
     * @param model \u805a\u5408\u6839\u540d\u79f0\u6216\u6a21\u578b
     * @param id \u805a\u5408\u6839\u6807\u8bc6
     * @return \u805a\u5408\u6839\u8be6\u60c5
     */
    @GetMapping("/{model}/detail/{id}")
    default AggregateRoot<?> detail(@PathVariable("model") String model,
                                    @PathVariable("id") String id) {
        return (AggregateRoot<?>) repository(model).findById(id).orElse(null);
    }

    /**
     * \u68c0\u67e5\u805a\u5408\u6839\u662f\u5426\u5b58\u5728\u3002
     * @param model \u805a\u5408\u6839\u540d\u79f0\u6216\u6a21\u578b
     * @param params \u8bf7\u6c42\u53c2\u6570
     * @return \u805a\u5408\u6839\u662f\u5426\u5b58\u5728
     */
    @GetMapping("/{model}/exist")
    default Boolean exist(@PathVariable("model") String model,
                          @RequestParam Map<String, Object> params) {
        return query(model, params).exist();
    }

    /**
     * \u7edf\u8ba1\u7b26\u5408\u6761\u4ef6\u7684\u805a\u5408\u6839\u3002
     * @param model \u805a\u5408\u6839\u540d\u79f0\u6216\u6a21\u578b
     * @param params \u8bf7\u6c42\u53c2\u6570
     * @return \u805a\u5408\u6839\u6570\u91cf
     */
    @GetMapping("/{model}/count")
    default Long count(@PathVariable("model") String model,
                       @RequestParam Map<String, Object> params) {
        return query(model, params).count();
    }

    /**
     * \u521b\u5efa\u805a\u5408\u6839\u3002
     * @param model \u805a\u5408\u6839\u540d\u79f0\u6216\u6a21\u578b
     * @param body \u8bf7\u6c42\u4f53\u53c2\u6570
     * @return \u521b\u5efa\u540e\u7684\u805a\u5408\u6839
     */
    @PostMapping("/{model}/create")
    default AggregateRoot<?> create(@PathVariable("model") String model,
                                    @RequestBody Map<String, Object> body) {
        AggregateRoot<?> aggregate = aggregate(model, body);
        return (AggregateRoot<?>) repository(model).save(aggregate);
    }

    /**
     * \u6279\u91cf\u4fdd\u5b58\u805a\u5408\u6839\u3002
     * @param model \u805a\u5408\u6839\u540d\u79f0\u6216\u6a21\u578b
     * @param body \u8bf7\u6c42\u4f53\u53c2\u6570
     */
    @PostMapping("/{model}/saveBatch")
    default void saveBatch(@PathVariable("model") String model,
                           @RequestBody List<Map<String, Object>> body) {
        Repository repository = repository(model);
        for (AggregateRoot<?> aggregate : aggregates(model, body)) {
            repository.save(aggregate);
        }
    }

    /**
     * \u66f4\u65b0\u805a\u5408\u6839\u3002
     * @param model \u805a\u5408\u6839\u540d\u79f0\u6216\u6a21\u578b
     * @param body \u8bf7\u6c42\u4f53\u53c2\u6570
     */
    @PostMapping({"/{model}/update", "/{model}/modify"})
    default void update(@PathVariable("model") String model,
                        @RequestBody Map<String, Object> body) {
        AggregateRoot<?> aggregate = aggregate(model, body);
        repository(model).save(aggregate);
    }

    /**
     * \u6279\u91cf\u66f4\u65b0\u805a\u5408\u6839\u3002
     * @param model \u805a\u5408\u6839\u540d\u79f0\u6216\u6a21\u578b
     * @param body \u8bf7\u6c42\u4f53\u53c2\u6570
     */
    @PostMapping({"/{model}/updateBatch", "/{model}/modifyBatch"})
    default void updateBatch(@PathVariable("model") String model,
                             @RequestBody List<Map<String, Object>> body) {
        Repository repository = repository(model);
        for (AggregateRoot<?> aggregate : aggregates(model, body)) {
            repository.save(aggregate);
        }
    }

    /**
     * \u4fdd\u5b58\u805a\u5408\u6839\u3002
     * @param model \u805a\u5408\u6839\u540d\u79f0\u6216\u6a21\u578b
     * @param body \u8bf7\u6c42\u4f53\u53c2\u6570
     * @return \u4fdd\u5b58\u540e\u7684\u805a\u5408\u6839
     */
    @PostMapping("/{model}/save")
    default AggregateRoot<?> save(@PathVariable("model") String model,
                                  @RequestBody Map<String, Object> body) {
        AggregateRoot<?> aggregate = aggregate(model, body);
        return (AggregateRoot<?>) repository(model).save(aggregate);
    }

    /**
     * \u5220\u9664\u805a\u5408\u6839\u3002
     * @param model \u805a\u5408\u6839\u540d\u79f0\u6216\u6a21\u578b
     * @param id \u805a\u5408\u6839\u6807\u8bc6
     */
    @PostMapping({"/{model}/delete/{id}", "/{model}/remove/{id}"})
    default void delete(@PathVariable("model") String model,
                        @PathVariable("id") String id) {
        repository(model).deleteById(id);
    }

    /**
     * \u6309\u67e5\u8be2\u6761\u4ef6\u5220\u9664\u805a\u5408\u6839\u3002
     * @param model \u805a\u5408\u6839\u540d\u79f0\u6216\u6a21\u578b
     * @param body \u8bf7\u6c42\u4f53\u53c2\u6570
     */
    @PostMapping("/{model}/remove")
    default void removeByQuery(@PathVariable("model") String model,
                               @RequestBody Map<String, Object> body) {
        Query<?> query = query(model, body);
        Repository richRepository = richRepository(model);
        richRepository.deleteByQuery(query);
    }

    /**
     * 执行 <code>repository</code>，调用 <code>RepositoryRegistry.repository</code>。
     *
     * @param model 传入此操作的 <code>String</code> 类型 <code>model</code>
     * @return 返回表达式 <code>RepositoryRegistry.repository((Class) modelClass(model))</code>
     */
    private Repository repository(String model) {
        return RepositoryRegistry.repository((Class) modelClass(model));
    }

    /**
     * 按 <code>rich Repository</code> 的契约计算并返回结果。
     *
     * @param model 传入此操作的 <code>String</code> 类型 <code>model</code>
     * @return 返回表达式 <code>repository(model)</code>
     */
    private Repository richRepository(String model) {
        return repository(model);
    }

    /**
     * 执行 <code>query</code>，调用 <code>BeanKit.ofMap</code>。
     *
     * @param model 传入此操作的 <code>String</code> 类型 <code>model</code>
     * @param source 传入此操作的 <code>Map&lt;String, Object&gt;</code> 类型 <code>source</code>
     * @return 按条件返回 <code>query</code>、<code>newInstance(queryClass)</code>
     */
    private Query<?> query(String model, Map<String, Object> source) {
        Class<? extends Query> queryClass = queryClass(model);
        Query<?> query = BeanKit.ofMap(source, queryClass);
        if (Objects.nonNull(query)) {
            return query;
        }
        return newInstance(queryClass);
    }

    /**
     * 执行 <code>aggregate</code>，调用 <code>BeanKit.ofMap</code>。
     *
     * @param model 传入此操作的 <code>String</code> 类型 <code>model</code>
     * @param source 传入此操作的 <code>Map&lt;String, Object&gt;</code> 类型 <code>source</code>
     * @return 按条件返回 <code>aggregate</code>、<code>newInstance(modelClass)</code>
     */
    private AggregateRoot<?> aggregate(String model, Map<String, Object> source) {
        Class<? extends AggregateRoot<?>> modelClass = modelClass(model);
        AggregateRoot<?> aggregate = BeanKit.ofMap(source, modelClass);
        if (Objects.nonNull(aggregate)) {
            return aggregate;
        }
        return newInstance(modelClass);
    }

    /**
     * 执行 <code>aggregates</code>，调用 <code>aggregates.add</code>。
     *
     * @param model 传入此操作的 <code>String</code> 类型 <code>model</code>
     * @param source 传入此操作的 <code>List&lt;Map&lt;String, Object&gt;&gt;</code> 类型 <code>source</code>
     * @return 按条件返回 <code>aggregates</code>、<code>aggregates</code>
     */
    private List<AggregateRoot<?>> aggregates(String model, List<Map<String, Object>> source) {
        List<AggregateRoot<?>> aggregates = new ArrayList<>();
        if (Objects.isNull(source)) {
            return aggregates;
        }
        for (Map<String, Object> row : source) {
            aggregates.add(aggregate(model, row));
        }
        return aggregates;
    }

    /**
     * 执行 <code>model Class</code>，调用 <code>MappingKit.get</code>、<code>class.isAssignableFrom</code>。
     *
     * @param model 传入此操作的 <code>String</code> 类型 <code>model</code>
     * @return 返回表达式 <code>(Class&lt;? extends AggregateRoot&lt;?&gt;&gt;) mappedClass</code>
     */
    private Class<? extends AggregateRoot<?>> modelClass(String model) {
        Object mapped = MappingKit.get("MODEL_NAME", model);
        if (mapped instanceof Class<?> mappedClass && AggregateRoot.class.isAssignableFrom(mappedClass)) {
            return (Class<? extends AggregateRoot<?>>) mappedClass;
        }
        throw new BizRuntimeException("Aggregate model mapping not found for {}", model);
    }

    /**
     * 执行 <code>query Class</code>，调用 <code>MappingKit.get</code>、<code>class.isAssignableFrom</code>。
     *
     * @param model 传入此操作的 <code>String</code> 类型 <code>model</code>
     * @return 返回表达式 <code>(Class&lt;? extends Query&gt;) mappedClass</code>
     */
    private Class<? extends Query> queryClass(String model) {
        Object mapped = MappingKit.get("MODEL_QUERY", modelClass(model));
        if (mapped instanceof Class<?> mappedClass && Query.class.isAssignableFrom(mappedClass)) {
            return (Class<? extends Query>) mappedClass;
        }
        throw new BizRuntimeException("Query mapping not found for model {}", model);
    }

    /**
     * 执行 <code>new Instance</code>，调用 <code>type.getDeclaredConstructor</code>、<code>type.getName</code>。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param type 传入此操作的 <code>Class&lt;T&gt;</code> 类型 <code>type</code>
     * @return 返回表达式 <code>type.getDeclaredConstructor().newInstance()</code>
     */
    private <T> T newInstance(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new BizRuntimeException("Cannot instantiate " + type.getName(), e);
        }
    }
}
