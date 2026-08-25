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
package io.ddd4j.sample.quarkus.satoken.rbac;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 权限内存仓储。
 *
 * <p>使用 {@link ApplicationScoped} 暴露为 CDI 单例 Bean；底层为 {@link ConcurrentHashMap}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ApplicationScoped
public class InMemoryPermissionRepository {

    private final ConcurrentHashMap<String, Permission> store = new ConcurrentHashMap<>();

    /**
     * 保存或覆盖权限。
     */
    public Permission save(Permission permission) {
        Objects.requireNonNull(permission, "permission");
        store.put(permission.getCode(), permission);
        return permission;
    }

    /**
     * 按编码查询。
     */
    public Optional<Permission> findByCode(String code) {
        return Optional.ofNullable(store.get(code));
    }

    /**
     * 查询全部。
     */
    public List<Permission> findAll() {
        return new ArrayList<>(store.values());
    }

    /**
     * 删除权限。
     */
    public boolean deleteByCode(String code) {
        return Objects.nonNull(store.remove(code));
    }

    /**
     * 当前存储的权限数量。
     */
    public int size() {
        return store.size();
    }

    /**
     * 清空内存数据，供可重复的样例测试重建金标数据。
     */
    public void clear() {
        store.clear();
    }

}
