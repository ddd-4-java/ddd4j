/*
 * Copyright 2017-2026 the original author hiwepy.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.web.webmvc.controller;

import io.ddd4j.core.contract.Model;
import io.ddd4j.core.contract.Page;
import io.ddd4j.core.contract.R;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.Serializable;

/**
 * P1-5: 聚合根 CRUD Controller 模板
 * <p>
 * 业务项目的聚合根 Controller 应继承此类，类型参数：
 * </p>
 * <ul>
 *   <li>{@code M}：聚合根领域模型（{@link Model} 子接口）</li>
 *   <li>{@code Q}：查询参数对象</li>
 *   <li>{@code ID}：主键类型</li>
 * </ul>
 *
 * <p>标准路由：
 * <pre>
 *   GET    /                  - 分页列表
 *   GET    /{id}              - 详情
 *   POST   /                  - 新增
 *   PUT    /{id}              - 修改
 *   DELETE /{id}              - 删除
 *   POST   /{id}:disable      - 禁用（业务行为）
 *   POST   /{id}:enable       - 启用（业务行为）
 * </pre>
 * </p>
 *
 * @param <M>  聚合根模型
 * @param <Q>  查询参数
 * @param <ID> 主键
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public abstract class BaseAggregateController<M extends Model, Q, ID extends Serializable> {

    /**
     * 分页查询
     */
    @GetMapping
    public R<Page<M>> page(@RequestParam(defaultValue = "1") int pageNum,
                           @RequestParam(defaultValue = "20") int pageSize,
                           Q query) {
        return R.ok(listPage(pageNum, pageSize, query));
    }

    /**
     * 详情
     */
    @GetMapping("/{id}")
    public R<M> getById(@PathVariable("id") ID id) {
        return R.ok(detail(id));
    }

    /**
     * 新增
     */
    @PostMapping
    public R<M> create(@RequestBody M model) {
        return R.ok(save(model));
    }

    /**
     * 修改
     */
    @PutMapping("/{id}")
    public R<M> update(@PathVariable("id") ID id, @RequestBody M model) {
        return R.ok(modify(id, model));
    }

    /**
     * 删除
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") ID id) {
        remove(id);
        return R.ok();
    }

    /**
     * 业务行为：禁用（聚合根业务行为）
     */
    @PostMapping("/{id}:disable")
    public R<M> disable(@PathVariable("id") ID id) {
        return R.ok(doDisable(id));
    }

    /**
     * 业务行为：启用
     */
    @PostMapping("/{id}:enable")
    public R<M> enable(@PathVariable("id") ID id) {
        return R.ok(doEnable(id));
    }

    // ------- 业务项目实现以下方法 -------

    protected abstract Page<M> listPage(int pageNum, int pageSize, Q query);

    protected abstract M detail(ID id);

    protected abstract M save(M model);

    protected abstract M modify(ID id, M model);

    protected abstract void remove(ID id);

    protected abstract M doDisable(ID id);

    protected abstract M doEnable(ID id);

}
