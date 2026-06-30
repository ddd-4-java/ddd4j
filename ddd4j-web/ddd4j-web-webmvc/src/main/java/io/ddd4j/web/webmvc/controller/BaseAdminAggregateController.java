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

import io.ddd4j.core.contract.Page;
import io.ddd4j.core.contract.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * P1-5: 管理端聚合根 Controller 模板
 * <p>
 * 与 {@link BaseAggregateController} 的区别：
 * </p>
 * <ul>
 *   <li>仅分页查询（GET），写操作由 {@link BaseAggregateController} 承担</li>
 *   <li>面向管理端后台，列表可包含禁用、逻辑删除等敏感字段</li>
 *   <li>通常配合 Spring Security {@code @PreAuthorize} 鉴权</li>
 * </ul>
 *
 * <p>建议在子类上使用 {@code @PreAuthorize("hasRole('ADMIN')")} 限定权限。
 * </p>
 *
 * @param <V> 管理端视图对象（VO）
 * @param <Q> 查询参数
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public abstract class BaseAdminAggregateController<V, Q> {

    @GetMapping
    @ResponseBody
    public R<Page<V>> page(@RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "20") int pageSize, Q query) {
        return R.ok(adminListPage(pageNum, pageSize, query));
    }

    // ------- 业务项目实现以下方法 -------

    protected abstract Page<V> adminListPage(int pageNum, int pageSize, Q query);

}
