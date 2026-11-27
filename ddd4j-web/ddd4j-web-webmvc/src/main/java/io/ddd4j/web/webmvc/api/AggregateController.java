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

import io.ddd4j.core.api.Page;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.kit.lang.BeanKit;
import io.ddd4j.kit.lang.MappingKit;
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
     * 按请求体分页查询聚合根。
     *
     * @param model 聚合根名称或模型
     * @param body  请求体参数
     * @return 聚合根分页结果
     */
    @PostMapping("/{model}/page")
    default Page<? extends AggregateRoot<?>> postPage(@PathVariable("model") String model,
                                                      @RequestBody Map<String, Object> body) {
        return AggregateControllerSupport.query(model, body).page();
    }

    /**
     * 按查询参数分页查询聚合根。
     *
     * @param model  聚合根名称或模型
     * @param params 请求参数
     * @return 聚合根分页结果
     */
    @GetMapping("/{model}/page")
    default Page<? extends AggregateRoot<?>> getPage(@PathVariable("model") String model,
                                                     @RequestParam Map<String, Object> params) {
        return AggregateControllerSupport.query(model, params).page();
    }

    /**
     * 按请求体查询聚合根列表。
     *
     * @param model 聚合根名称或模型
     * @param body  请求体参数
     * @return 聚合根列表
     */
    @PostMapping("/{model}/list")
    default List<? extends AggregateRoot<?>> postList(@PathVariable("model") String model,
                                                      @RequestBody Map<String, Object> body) {
        return AggregateControllerSupport.query(model, body).list();
    }

    /**
     * 按查询参数查询聚合根列表。
     *
     * @param model  聚合根名称或模型
     * @param params 请求参数
     * @return 聚合根列表
     */
    @GetMapping("/{model}/list")
    default List<? extends AggregateRoot<?>> getList(@PathVariable("model") String model,
                                                     @RequestParam Map<String, Object> params) {
        return AggregateControllerSupport.query(model, params).list();
    }

    /**
     * 查询聚合根详情。
     *
     * @param model  聚合根名称或模型
     * @param params 请求参数
     * @return 聚合根详情
     */
    @GetMapping("/{model}/detail")
    default AggregateRoot<?> detail(@PathVariable("model") String model,
                                    @RequestParam Map<String, Object> params) {
        return AggregateControllerSupport.query(model, params).first();
    }

    /**
     * 查询聚合根详情。
     *
     * @param model 聚合根名称或模型
     * @param id    聚合根标识
     * @return 聚合根详情
     */
    @GetMapping("/{model}/detail/{id}")
    default AggregateRoot<?> detail(@PathVariable("model") String model,
                                    @PathVariable("id") String id) {
        return (AggregateRoot<?>) AggregateControllerSupport.repository(model).findById(id).orElse(null);
    }

    /**
     * 检查聚合根是否存在。
     *
     * @param model  聚合根名称或模型
     * @param params 请求参数
     * @return 聚合根是否存在
     */
    @GetMapping("/{model}/exist")
    default Boolean exist(@PathVariable("model") String model,
                          @RequestParam Map<String, Object> params) {
        return AggregateControllerSupport.query(model, params).exist();
    }

    /**
     * 统计符合条件的聚合根。
     *
     * @param model  聚合根名称或模型
     * @param params 请求参数
     * @return 聚合根数量
     */
    @GetMapping("/{model}/count")
    default Long count(@PathVariable("model") String model,
                       @RequestParam Map<String, Object> params) {
        return AggregateControllerSupport.query(model, params).count();
    }

    /**
     * 创建聚合根。
     *
     * @param model 聚合根名称或模型
     * @param body  请求体参数
     * @return 创建后的聚合根
     */
    @PostMapping("/{model}/create")
    default AggregateRoot<?> create(@PathVariable("model") String model,
                                    @RequestBody Map<String, Object> body) {
        AggregateRoot<?> aggregate = AggregateControllerSupport.aggregate(model, body);
        return (AggregateRoot<?>) AggregateControllerSupport.repository(model).save(aggregate);
    }

    /**
     * 批量保存聚合根。
     *
     * @param model 聚合根名称或模型
     * @param body  请求体参数
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
     * 更新聚合根。
     *
     * @param model 聚合根名称或模型
     * @param body  请求体参数
     */
    @PostMapping({"/{model}/update", "/{model}/modify"})
    default void update(@PathVariable("model") String model,
                        @RequestBody Map<String, Object> body) {
        AggregateRoot<?> aggregate = AggregateControllerSupport.aggregate(model, body);
        AggregateControllerSupport.repository(model).save(aggregate);
    }

    /**
     * 批量更新聚合根。
     *
     * @param model 聚合根名称或模型
     * @param body  请求体参数
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
     * 保存聚合根。
     *
     * @param model 聚合根名称或模型
     * @param body  请求体参数
     * @return 保存后的聚合根
     */
    @PostMapping("/{model}/save")
    default AggregateRoot<?> save(@PathVariable("model") String model,
                                  @RequestBody Map<String, Object> body) {
        AggregateRoot<?> aggregate = AggregateControllerSupport.aggregate(model, body);
        return (AggregateRoot<?>) AggregateControllerSupport.repository(model).save(aggregate);
    }

    /**
     * 删除聚合根。
     *
     * @param model 聚合根名称或模型
     * @param id    聚合根标识
     */
    @PostMapping({"/{model}/delete/{id}", "/{model}/remove/{id}"})
    default void delete(@PathVariable("model") String model,
                        @PathVariable("id") String id) {
        AggregateControllerSupport.repository(model).deleteById(id);
    }

    /**
     * 按查询条件删除聚合根。
     *
     * @param model 聚合根名称或模型
     * @param body  请求体参数
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
 * 为聚合 HTTP 入口定位仓储和领域模型并处理通用数据转换。
 */
@SuppressWarnings({"rawtypes", "unchecked"})
final class AggregateControllerSupport {

    /**
     * 限制调用方直接创建 <code>AggregateControllerSupport</code> 实例。
     */
    private AggregateControllerSupport() {
    }

    /**
     * 按领域模型名称定位仓储。
     *
     * @param model 仓储注册表中使用的领域模型名称
     * @return 当前模型仓储
     */
    static Repository repository(String model) {
        return RepositoryRegistry.repository((Class) modelClass(model));
    }

    /**
     * 取得支持丰富查询和写入操作的仓储。
     *
     * @param model 仓储注册表中使用的领域模型名称
     * @return 当前模型的丰富仓储入口
     */
    static Repository richRepository(String model) {
        return repository(model);
    }

    /**
     * 使用请求输入绑定领域模型的查询对象。
     *
     * @param model  仓储注册表中使用的领域模型名称
     * @param source 用来绑定领域模型或查询对象的请求字段
     * @return 绑定请求条件的领域查询
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
     * 以请求字段绑定当前领域聚合。
     *
     * @param model  仓储注册表中使用的领域模型名称
     * @param source 用来绑定领域模型或查询对象的请求字段
     * @return 绑定请求字段的聚合
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
     * 把批量输入逐项绑定为当前领域聚合类型。
     *
     * @param model  仓储注册表中使用的领域模型名称
     * @param source 用来绑定领域模型或查询对象的请求字段
     * @return 按输入顺序构建的领域聚合列表
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
     * 解析当前仓储管理的领域模型类型。
     *
     * @param model 仓储注册表中使用的领域模型名称
     * @return 当前领域模型类型
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
     * 解析当前仓储可使用的业务查询类型。
     *
     * @param model 仓储注册表中使用的领域模型名称
     * @return 当前领域查询类型
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
     * 创建输入绑定需要的 Java 模型实例。
     *
     * @param <T>  框架处理的目标 Java 类型
     * @param type 目标 Java 类型或声明的泛型类型
     * @return 成功创建的目标类型实例
     */
    static <T> T newInstance(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new BizRuntimeException("Cannot instantiate " + type.getName(), e);
        }
    }
}
