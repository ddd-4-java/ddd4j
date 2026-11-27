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
package io.ddd4j.web.webmvc.controller;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.api.R;
import io.ddd4j.core.ddd.model.AggregateRoot;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;

/**
 * P1-5: 聚合根 CRUD Controller 模板
 * <p>
 * 业务项目的聚合根 Controller 应继承此类，类型参数：
 * </p>
 * <ul>
 *   <li>{@code M}：聚合根领域模型（{@link AggregateRoot} 子类）</li>
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
 *
 * @param <M>  聚合根模型
 * @param <Q>  查询参数
 * @param <ID> 主键
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public abstract class BaseAggregateController<M extends AggregateRoot<?>, Q, ID extends Serializable> {

    /**
     * 分页查询
     * @param pageNum 页数值
     * @param pageSize 页大小
     * @param query 查询条件
     * @return 返回的 R 结果
     */
    @GetMapping
    public R<Page<M>> page(@RequestParam(defaultValue = "1") int pageNum,
                           @RequestParam(defaultValue = "20") int pageSize,
                           Q query) {
        return R.ok(listPage(pageNum, pageSize, query));
    }

    /**
     * 详情
     * @param id 标识
     * @return 获取的R
     */
    @GetMapping("/{id}")
    public R<M> getById(@PathVariable ID id) {
        return R.ok(detail(id));
    }

    /**
     * 新增
     * @param model 模型对象
     * @return 创建的R
     */
    @PostMapping
    public R<M> create(@RequestBody M model) {
        return R.ok(save(model));
    }

    /**
     * 修改
     * @param id 标识
     * @param model 模型对象
     * @return 更新后的R
     */
    @PutMapping("/{id}")
    public R<M> update(@PathVariable("id") ID id, @RequestBody M model) {
        return R.ok(modify(id, model));
    }

    /**
     * 删除
     * @param id 标识
     * @return 删除后的R
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") ID id) {
        remove(id);
        return R.ok();
    }

    /**
     * 业务行为：禁用（聚合根业务行为）
     * @param id 标识
     * @return 返回的 R 结果
     */
    @PostMapping("/{id}:disable")
    public R<M> disable(@PathVariable("id") ID id) {
        return R.ok(doDisable(id));
    }

    /**
     * 业务行为：启用
     * @param id 标识
     * @return 返回的 R 结果
     */
    @PostMapping("/{id}:enable")
    public R<M> enable(@PathVariable("id") ID id) {
        return R.ok(doEnable(id));
    }

    // ------- 业务项目实现以下方法 -------

    /**
     * \u5206\u9875\u67e5\u8be2\u805a\u5408\u6839\u3002
     * @param pageNum \u9875\u7801
     * @param pageSize \u6bcf\u9875\u6570\u91cf
     * @param query \u67e5\u8be2\u6761\u4ef6
     * @return \u805a\u5408\u6839\u5206\u9875\u7ed3\u679c
     */
    protected abstract Page<M> listPage(int pageNum, int pageSize, Q query);

    /**
     * \u67e5\u8be2\u805a\u5408\u6839\u8be6\u60c5\u3002
     * @param id \u805a\u5408\u6839\u6807\u8bc6
     * @return \u805a\u5408\u6839\u8be6\u60c5
     */
    protected abstract M detail(ID id);

    /**
     * \u4fdd\u5b58\u805a\u5408\u6839\u3002
     * @param model \u805a\u5408\u6839\u540d\u79f0\u6216\u6a21\u578b
     * @return \u4fdd\u5b58\u540e\u7684\u805a\u5408\u6839
     */
    protected abstract M save(M model);

    /**
     * \u4fee\u6539\u805a\u5408\u6839\u3002
     * @param id \u805a\u5408\u6839\u6807\u8bc6
     * @param model \u805a\u5408\u6839\u540d\u79f0\u6216\u6a21\u578b
     * @return \u4fee\u6539\u540e\u7684\u805a\u5408\u6839
     */
    protected abstract M modify(ID id, M model);

    /**
     * \u5220\u9664\u805a\u5408\u6839\u3002
     * @param id \u805a\u5408\u6839\u6807\u8bc6
     */
    protected abstract void remove(ID id);

    /**
     * \u7981\u7528\u805a\u5408\u6839\u3002
     * @param id \u805a\u5408\u6839\u6807\u8bc6
     * @return \u7981\u7528\u540e\u7684\u805a\u5408\u6839
     */
    protected abstract M doDisable(ID id);

    /**
     * \u542f\u7528\u805a\u5408\u6839\u3002
     * @param id \u805a\u5408\u6839\u6807\u8bc6
     * @return \u542f\u7528\u540e\u7684\u805a\u5408\u6839
     */
    protected abstract M doEnable(ID id);

}
