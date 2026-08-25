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
package io.ddd4j.sample.javalin.satoken.rbac.domain.repository;

import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.sample.javalin.satoken.rbac.domain.model.User;

import java.util.List;
import java.util.Optional;

/**
 * 用户仓储接口（RBAC）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface UserRepository extends Repository<User, String> {

    /**
     * 查询全部用户。
     */
    List<User> findAll();

    /**
     * 按用户名查找。
     */
    Optional<User> findByUsername(String username);

    /**
     * 按状态过滤查询用户列表。
     */
    List<User> findByStatus(User.Status status);
}