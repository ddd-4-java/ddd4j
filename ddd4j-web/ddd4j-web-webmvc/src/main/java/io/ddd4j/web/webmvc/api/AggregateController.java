/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.web.webmvc.api;
import io.ddd4j.kit.lang.MappingKit;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.core.exception.BizRuntimeException;
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
        return AggregateControllerSupport.query(model, body).page();
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
        return AggregateControllerSupport.query(model, params).page();
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
        return AggregateControllerSupport.query(model, body).list();
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
        return AggregateControllerSupport.query(model, params).list();
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
        return AggregateControllerSupport.query(model, params).first();
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
        return (AggregateRoot<?>) AggregateControllerSupport.repository(model).findById(id).orElse(null);
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
        return AggregateControllerSupport.query(model, params).exist();
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
        return AggregateControllerSupport.query(model, params).count();
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
        AggregateRoot<?> aggregate = AggregateControllerSupport.aggregate(model, body);
        return (AggregateRoot<?>) AggregateControllerSupport.repository(model).save(aggregate);
    }

    /**
     * \u6279\u91cf\u4fdd\u5b58\u805a\u5408\u6839\u3002
     * @param model \u805a\u5408\u6839\u540d\u79f0\u6216\u6a21\u578b
     * @param body \u8bf7\u6c42\u4f53\u53c2\u6570
     */
    @PostMapping("/{model}/saveBatch")
    default void saveBatch(@PathVariable("model") String model,
                           @RequestBody List<Map<String, Object>> body) {
        Repository repository = AggregateControllerSupport.repository(model);
        for (AggregateRoot<?> aggregate : AggregateControllerSupport.aggregates(model, body)) {
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
        AggregateRoot<?> aggregate = AggregateControllerSupport.aggregate(model, body);
        AggregateControllerSupport.repository(model).save(aggregate);
    }

    /**
     * \u6279\u91cf\u66f4\u65b0\u805a\u5408\u6839\u3002
     * @param model \u805a\u5408\u6839\u540d\u79f0\u6216\u6a21\u578b
     * @param body \u8bf7\u6c42\u4f53\u53c2\u6570
     */
    @PostMapping({"/{model}/updateBatch", "/{model}/modifyBatch"})
    default void updateBatch(@PathVariable("model") String model,
                             @RequestBody List<Map<String, Object>> body) {
        Repository repository = AggregateControllerSupport.repository(model);
        for (AggregateRoot<?> aggregate : AggregateControllerSupport.aggregates(model, body)) {
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
        AggregateRoot<?> aggregate = AggregateControllerSupport.aggregate(model, body);
        return (AggregateRoot<?>) AggregateControllerSupport.repository(model).save(aggregate);
    }

    /**
     * \u5220\u9664\u805a\u5408\u6839\u3002
     * @param model \u805a\u5408\u6839\u540d\u79f0\u6216\u6a21\u578b
     * @param id \u805a\u5408\u6839\u6807\u8bc6
     */
    @PostMapping({"/{model}/delete/{id}", "/{model}/remove/{id}"})
    default void delete(@PathVariable("model") String model,
                        @PathVariable("id") String id) {
        AggregateControllerSupport.repository(model).deleteById(id);
    }

    /**
     * \u6309\u67e5\u8be2\u6761\u4ef6\u5220\u9664\u805a\u5408\u6839\u3002
     * @param model \u805a\u5408\u6839\u540d\u79f0\u6216\u6a21\u578b
     * @param body \u8bf7\u6c42\u4f53\u53c2\u6570
     */
    @PostMapping("/{model}/remove")
    default void removeByQuery(@PathVariable("model") String model,
                               @RequestBody Map<String, Object> body) {
        Query<?> query = AggregateControllerSupport.query(model, body);
        Repository richRepository = AggregateControllerSupport.richRepository(model);
        richRepository.deleteByQuery(query);
    }

}

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
@SuppressWarnings({"rawtypes", "unchecked"})
final class AggregateControllerSupport {

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>AggregateControllerSupport</code> \u5b9e\u4f8b\u3002 */
    private AggregateControllerSupport() {
    }

    /**
     * \u6309\u9886\u57df\u6a21\u578b\u540d\u79f0\u5b9a\u4f4d\u4ed3\u50a8\u3002
     * @param model \u4ed3\u50a8\u6ce8\u518c\u8868\u4e2d\u4f7f\u7528\u7684\u9886\u57df\u6a21\u578b\u540d\u79f0
     * @return \u5f53\u524d\u6a21\u578b\u4ed3\u50a8
     */
    static Repository repository(String model) {
        return RepositoryRegistry.repository((Class) modelClass(model));
    }

    /**
     * \u53d6\u5f97\u652f\u6301\u4e30\u5bcc\u67e5\u8be2\u548c\u5199\u5165\u64cd\u4f5c\u7684\u4ed3\u50a8\u3002
     * @param model \u4ed3\u50a8\u6ce8\u518c\u8868\u4e2d\u4f7f\u7528\u7684\u9886\u57df\u6a21\u578b\u540d\u79f0
     * @return \u5f53\u524d\u6a21\u578b\u7684\u4e30\u5bcc\u4ed3\u50a8\u5165\u53e3
     */
    static Repository richRepository(String model) {
        return repository(model);
    }

    /**
     * \u4f7f\u7528\u8bf7\u6c42\u8f93\u5165\u7ed1\u5b9a\u9886\u57df\u6a21\u578b\u7684\u67e5\u8be2\u5bf9\u8c61\u3002
     * @param model \u4ed3\u50a8\u6ce8\u518c\u8868\u4e2d\u4f7f\u7528\u7684\u9886\u57df\u6a21\u578b\u540d\u79f0
     * @param source \u7528\u6765\u7ed1\u5b9a\u9886\u57df\u6a21\u578b\u6216\u67e5\u8be2\u5bf9\u8c61\u7684\u8bf7\u6c42\u5b57\u6bb5
     * @return \u7ed1\u5b9a\u8bf7\u6c42\u6761\u4ef6\u7684\u9886\u57df\u67e5\u8be2
     */
    static Query<?> query(String model, Map<String, Object> source) {
        Class<? extends Query> queryClass = queryClass(model);
        Query<?> query = BeanKit.ofMap(source, queryClass);
        if (Objects.nonNull(query)) {
            return query;
        }
        return newInstance(queryClass);
    }

    /**
     * \u4ee5\u8bf7\u6c42\u5b57\u6bb5\u7ed1\u5b9a\u5f53\u524d\u9886\u57df\u805a\u5408\u3002
     * @param model \u4ed3\u50a8\u6ce8\u518c\u8868\u4e2d\u4f7f\u7528\u7684\u9886\u57df\u6a21\u578b\u540d\u79f0
     * @param source \u7528\u6765\u7ed1\u5b9a\u9886\u57df\u6a21\u578b\u6216\u67e5\u8be2\u5bf9\u8c61\u7684\u8bf7\u6c42\u5b57\u6bb5
     * @return \u7ed1\u5b9a\u8bf7\u6c42\u5b57\u6bb5\u7684\u805a\u5408
     */
    static AggregateRoot<?> aggregate(String model, Map<String, Object> source) {
        Class<? extends AggregateRoot<?>> modelClass = modelClass(model);
        AggregateRoot<?> aggregate = BeanKit.ofMap(source, modelClass);
        if (Objects.nonNull(aggregate)) {
            return aggregate;
        }
        return newInstance(modelClass);
    }

    /**
     * \u628a\u6279\u91cf\u8f93\u5165\u9010\u9879\u7ed1\u5b9a\u4e3a\u5f53\u524d\u9886\u57df\u805a\u5408\u7c7b\u578b\u3002
     * @param model \u4ed3\u50a8\u6ce8\u518c\u8868\u4e2d\u4f7f\u7528\u7684\u9886\u57df\u6a21\u578b\u540d\u79f0
     * @param source \u7528\u6765\u7ed1\u5b9a\u9886\u57df\u6a21\u578b\u6216\u67e5\u8be2\u5bf9\u8c61\u7684\u8bf7\u6c42\u5b57\u6bb5
     * @return \u6309\u8f93\u5165\u987a\u5e8f\u6784\u5efa\u7684\u9886\u57df\u805a\u5408\u5217\u8868
     */
    static List<AggregateRoot<?>> aggregates(String model, List<Map<String, Object>> source) {
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
     * \u89e3\u6790\u5f53\u524d\u4ed3\u50a8\u7ba1\u7406\u7684\u9886\u57df\u6a21\u578b\u7c7b\u578b\u3002
     * @param model \u4ed3\u50a8\u6ce8\u518c\u8868\u4e2d\u4f7f\u7528\u7684\u9886\u57df\u6a21\u578b\u540d\u79f0
     * @return \u5f53\u524d\u9886\u57df\u6a21\u578b\u7c7b\u578b
     */
    static Class<? extends AggregateRoot<?>> modelClass(String model) {
        Object mapped = MappingKit.get("MODEL_NAME", model);
        if (mapped instanceof Class
                && AggregateRoot.class.isAssignableFrom((Class<?>) mapped)) {
            return (Class<? extends AggregateRoot<?>>) mapped;
        }
        throw new BizRuntimeException("Aggregate model mapping not found for {}", model);
    }

    /**
     * \u89e3\u6790\u5f53\u524d\u4ed3\u50a8\u53ef\u4f7f\u7528\u7684\u4e1a\u52a1\u67e5\u8be2\u7c7b\u578b\u3002
     * @param model \u4ed3\u50a8\u6ce8\u518c\u8868\u4e2d\u4f7f\u7528\u7684\u9886\u57df\u6a21\u578b\u540d\u79f0
     * @return \u5f53\u524d\u9886\u57df\u67e5\u8be2\u7c7b\u578b
     */
    static Class<? extends Query> queryClass(String model) {
        Object mapped = MappingKit.get("MODEL_QUERY", modelClass(model));
        if (mapped instanceof Class
                && Query.class.isAssignableFrom((Class<?>) mapped)) {
            return (Class<? extends Query>) mapped;
        }
        throw new BizRuntimeException("Query mapping not found for model {}", model);
    }

    /**
     * \u521b\u5efa\u8f93\u5165\u7ed1\u5b9a\u9700\u8981\u7684 Java \u6a21\u578b\u5b9e\u4f8b\u3002
     * @param <T> \u6846\u67b6\u5904\u7406\u7684\u76ee\u6807 Java \u7c7b\u578b
     * @param type \u76ee\u6807 Java \u7c7b\u578b\u6216\u58f0\u660e\u7684\u6cdb\u578b\u7c7b\u578b
     * @return \u6210\u529f\u521b\u5efa\u7684\u76ee\u6807\u7c7b\u578b\u5b9e\u4f8b
     */
    static <T> T newInstance(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new BizRuntimeException("Cannot instantiate " + type.getName(), e);
        }
    }
}
