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

    @PostMapping("/{model}/page")
    default Page<? extends AggregateRoot<?>> postPage(@PathVariable("model") String model,
                                                      @RequestBody Map<String, Object> body) {
        return query(model, body).page();
    }

    @GetMapping("/{model}/page")
    default Page<? extends AggregateRoot<?>> getPage(@PathVariable("model") String model,
                                                     @RequestParam Map<String, Object> params) {
        return query(model, params).page();
    }

    @PostMapping("/{model}/list")
    default List<? extends AggregateRoot<?>> postList(@PathVariable("model") String model,
                                                      @RequestBody Map<String, Object> body) {
        return query(model, body).list();
    }

    @GetMapping("/{model}/list")
    default List<? extends AggregateRoot<?>> getList(@PathVariable("model") String model,
                                                     @RequestParam Map<String, Object> params) {
        return query(model, params).list();
    }

    @GetMapping("/{model}/detail")
    default AggregateRoot<?> detail(@PathVariable("model") String model,
                                    @RequestParam Map<String, Object> params) {
        return query(model, params).first();
    }

    @GetMapping("/{model}/detail/{id}")
    default AggregateRoot<?> detail(@PathVariable("model") String model,
                                    @PathVariable("id") String id) {
        return (AggregateRoot<?>) repository(model).findById(id).orElse(null);
    }

    @GetMapping("/{model}/exist")
    default Boolean exist(@PathVariable("model") String model,
                          @RequestParam Map<String, Object> params) {
        return query(model, params).exist();
    }

    @GetMapping("/{model}/count")
    default Long count(@PathVariable("model") String model,
                       @RequestParam Map<String, Object> params) {
        return query(model, params).count();
    }

    @PostMapping("/{model}/create")
    default AggregateRoot<?> create(@PathVariable("model") String model,
                                    @RequestBody Map<String, Object> body) {
        AggregateRoot<?> aggregate = aggregate(model, body);
        return (AggregateRoot<?>) repository(model).save(aggregate);
    }

    @PostMapping("/{model}/saveBatch")
    default void saveBatch(@PathVariable("model") String model,
                           @RequestBody List<Map<String, Object>> body) {
        Repository repository = repository(model);
        for (AggregateRoot<?> aggregate : aggregates(model, body)) {
            repository.save(aggregate);
        }
    }

    @PostMapping({"/{model}/update", "/{model}/modify"})
    default void update(@PathVariable("model") String model,
                        @RequestBody Map<String, Object> body) {
        AggregateRoot<?> aggregate = aggregate(model, body);
        repository(model).save(aggregate);
    }

    @PostMapping({"/{model}/updateBatch", "/{model}/modifyBatch"})
    default void updateBatch(@PathVariable("model") String model,
                             @RequestBody List<Map<String, Object>> body) {
        Repository repository = repository(model);
        for (AggregateRoot<?> aggregate : aggregates(model, body)) {
            repository.save(aggregate);
        }
    }

    @PostMapping("/{model}/save")
    default AggregateRoot<?> save(@PathVariable("model") String model,
                                  @RequestBody Map<String, Object> body) {
        AggregateRoot<?> aggregate = aggregate(model, body);
        return (AggregateRoot<?>) repository(model).save(aggregate);
    }

    @PostMapping({"/{model}/delete/{id}", "/{model}/remove/{id}"})
    default void delete(@PathVariable("model") String model,
                        @PathVariable("id") String id) {
        repository(model).deleteById(id);
    }

    @PostMapping("/{model}/remove")
    default void removeByQuery(@PathVariable("model") String model,
                               @RequestBody Map<String, Object> body) {
        Query<?> query = query(model, body);
        Repository richRepository = richRepository(model);
        richRepository.deleteByQuery(query);
    }

    default Repository repository(String model) {
        return RepositoryRegistry.repository((Class) modelClass(model));
    }

    default Repository richRepository(String model) {
        return repository(model);
    }

    default Query<?> query(String model, Map<String, Object> source) {
        Class<? extends Query> queryClass = queryClass(model);
        Query<?> query = BeanKit.ofMap(source, queryClass);
        if (Objects.nonNull(query)) {
            return query;
        }
        return newInstance(queryClass);
    }

    default AggregateRoot<?> aggregate(String model, Map<String, Object> source) {
        Class<? extends AggregateRoot<?>> modelClass = modelClass(model);
        AggregateRoot<?> aggregate = BeanKit.ofMap(source, modelClass);
        if (Objects.nonNull(aggregate)) {
            return aggregate;
        }
        return newInstance(modelClass);
    }

    default List<AggregateRoot<?>> aggregates(String model, List<Map<String, Object>> source) {
        List<AggregateRoot<?>> aggregates = new ArrayList<>();
        if (Objects.isNull(source)) {
            return aggregates;
        }
        for (Map<String, Object> row : source) {
            aggregates.add(aggregate(model, row));
        }
        return aggregates;
    }

    default Class<? extends AggregateRoot<?>> modelClass(String model) {
        Object mapped = MappingKit.get("MODEL_NAME", model);
        if (mapped instanceof Class
                && AggregateRoot.class.isAssignableFrom((Class<?>) mapped)) {
            return (Class<? extends AggregateRoot<?>>) mapped;
        }
        throw new BizRuntimeException("Aggregate model mapping not found for {}", model);
    }

    default Class<? extends Query> queryClass(String model) {
        Object mapped = MappingKit.get("MODEL_QUERY", modelClass(model));
        if (mapped instanceof Class
                && Query.class.isAssignableFrom((Class<?>) mapped)) {
            return (Class<? extends Query>) mapped;
        }
        throw new BizRuntimeException("Query mapping not found for model {}", model);
    }

    default <T> T newInstance(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new BizRuntimeException("Cannot instantiate " + type.getName(), e);
        }
    }
}
