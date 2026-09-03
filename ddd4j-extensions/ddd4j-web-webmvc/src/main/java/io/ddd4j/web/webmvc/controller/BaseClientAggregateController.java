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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.Serializable;

/**
 * P1-5: 客户端聚合根 Controller 模板
 * <p>
 * 与 {@link BaseAggregateController} 的区别：
 * </p>
 * <ul>
 *   <li>只读：仅提供 GET（分页 + 详情），不暴露写操作</li>
 *   <li>面向外部客户端，隐藏管理端字段（通过 {@code ClientVO} 投影）</li>
 *   <li>通常配合 Sa-Token / Spring Security 走 OAuth2 Resource Server</li>
 * </ul>
 *
 * @param <V>  客户端视图对象（VO）
 * @param <Q>  查询参数
 * @param <ID> 主键
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public abstract class BaseClientAggregateController<V, Q, ID extends Serializable> {

    @GetMapping
    public R<Page<V>> page(@RequestParam(defaultValue = "1") int pageNum,
                           @RequestParam(defaultValue = "20") int pageSize,
                           Q query) {
        return R.ok(clientListPage(pageNum, pageSize, query));
    }

    @GetMapping("/{id}")
    public R<V> getById(@PathVariable ID id) {
        return R.ok(clientDetail(id));
    }

    // ------- 业务项目实现以下方法（只读） -------

    protected abstract Page<V> clientListPage(int pageNum, int pageSize, Q query);

    protected abstract V clientDetail(ID id);

}
