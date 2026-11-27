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
import io.ddd4j.kit.lang.ReflectKit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Legacy typed CRUD controller backed by the new aggregate repository SPI.
 *
 * @param <M> aggregate root type
 * @param <Q> query type
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @deprecated prefer business-specific aggregate controllers.
 */
@Deprecated
@Slf4j(topic = "### BASE-WEB : CRUDController ###")
@SuppressWarnings({"unchecked", "rawtypes"})
public class CRUDController<M extends AggregateRoot<?>, Q extends Query<M>> {

    /** \u805a\u5408\u6839\u6301\u4e45\u5316\u4ed3\u5e93\u3002 */
    protected Repository<M, Serializable> repository;

    /**
     * \u4ece\u6cdb\u578b\u6a21\u578b\u7c7b\u578b\u67e5\u627e\u5e76\u7f13\u5b58\u5f53\u524d\u63a7\u5236\u5668\u4f7f\u7528\u7684\u4ed3\u50a8\u3002
     * @return \u5f53\u524d\u805a\u5408\u4ed3\u50a8\uff0c\u672a\u6ce8\u518c\u65f6\u4e3a\u7a7a
     */
    private Repository<M, Serializable> getRepository() {
        if (Objects.isNull(this.repository)) {
            Class<M> modelClass = ReflectKit.getSuperClassGenericType(this.getClass(), 0);
            this.repository = (Repository<M, Serializable>) RepositoryRegistry.repository(modelClass);
        }
        if (Objects.isNull(this.repository)) {
            log.error("未找到实体仓库");
        }
        return repository;
    }

    /**
     * \u5206\u9875\u67e5\u8be2\u805a\u5408\u6839\u3002
     * @param query \u67e5\u8be2\u6761\u4ef6
     * @return \u805a\u5408\u6839\u5206\u9875\u7ed3\u679c
     */
    @PostMapping("/page")
    public Page<M> page(@RequestBody Q query) {
        return query.page();
    }

    /**
     * \u6309\u67e5\u8be2\u53c2\u6570\u5206\u9875\u67e5\u8be2\u805a\u5408\u6839\u3002
     * @param query \u67e5\u8be2\u6761\u4ef6
     * @return \u805a\u5408\u6839\u5206\u9875\u7ed3\u679c
     */
    @GetMapping("/page")
    public Page<M> getPage(Q query) {
        return query.page();
    }

    /**
     * \u67e5\u8be2\u805a\u5408\u6839\u5217\u8868\u3002
     * @param query \u67e5\u8be2\u6761\u4ef6
     * @return \u805a\u5408\u6839\u5217\u8868
     */
    @PostMapping("/list")
    public List<M> list(@RequestBody Q query) {
        return query.list();
    }

    /**
     * \u6309\u67e5\u8be2\u53c2\u6570\u67e5\u8be2\u805a\u5408\u6839\u5217\u8868\u3002
     * @param query \u67e5\u8be2\u6761\u4ef6
     * @return \u805a\u5408\u6839\u5217\u8868
     */
    @GetMapping("/list")
    public List<M> getList(Q query) {
        return query.list();
    }

    /**
     * \u67e5\u8be2\u805a\u5408\u6839\u8be6\u60c5\u3002
     * @param query \u67e5\u8be2\u6761\u4ef6
     * @return \u805a\u5408\u6839\u8be6\u60c5
     */
    @GetMapping("/detail")
    public M detail(Q query) {
        return (M) query.first();
    }

    /**
     * \u67e5\u8be2\u805a\u5408\u6839\u8be6\u60c5\u3002
     * @param id \u805a\u5408\u6839\u6807\u8bc6
     * @return \u805a\u5408\u6839\u8be6\u60c5
     */
    @GetMapping("/detail/{id}")
    public M detail(@PathVariable("id") String id) {
        return getRepository().findById(id).orElse(null);
    }

    /**
     * \u4fdd\u5b58\u805a\u5408\u6839\u3002
     * @param model \u805a\u5408\u6839\u540d\u79f0\u6216\u6a21\u578b
     * @return \u4fdd\u5b58\u540e\u7684\u805a\u5408\u6839
     */
    @PostMapping({"/save", "/create"})
    public M save(@RequestBody M model) {
        return getRepository().save(model);
    }

    /**
     * \u6279\u91cf\u4fdd\u5b58\u805a\u5408\u6839\u3002
     * @param models \u805a\u5408\u6839\u5217\u8868
     */
    @PostMapping("/saveBatch")
    public void saveBatch(@RequestBody List<M> models) {
        if (Objects.isNull(models)) {
            return;
        }
        for (M model : models) {
            getRepository().save(model);
        }
    }

    /**
     * \u66f4\u65b0\u805a\u5408\u6839\u3002
     * @param model \u805a\u5408\u6839\u540d\u79f0\u6216\u6a21\u578b
     */
    @PostMapping({"/update", "/modify"})
    public void update(@RequestBody M model) {
        getRepository().save(model);
    }

    /**
     * \u5220\u9664\u805a\u5408\u6839\u3002
     * @param id \u805a\u5408\u6839\u6807\u8bc6
     */
    @PostMapping({"/delete/{id}", "/remove/{id}"})
    public void delete(@PathVariable("id") String id) {
        getRepository().deleteById(id);
    }
}
