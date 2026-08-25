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
package io.ddd4j.sample.javalin.shiro.rbac.domain;

import java.util.Objects;

/**
 * RBAC 权限码值对象。
 *
 * <p>权限码是 RBAC 体系中最小粒度的鉴权单元（如 {@code user:list} / {@code order:pay}），
 * 由 {@link Role#permissions()} 聚合后赋予 {@link User}。
 *
 * <p>本类在所有 7 个示例（Spring/Quarkus/Javalin × Sa-Token/Shiro/Security）中<b>完全一致</b>，
 * 证明切换底层鉴权框架时业务代码零改动。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class Permission {

    /**
     * 权限码（全局唯一，如 {@code user:list} / {@code order:pay} / {@code *}）
     */
    private final String code;
    /**
     * 权限描述（可选）
     */
    private final String description;

    public Permission(String code) {
        this(code, null);
    }

    public Permission(String code, String description) {
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.description = description;
    }

    public String code() {
        return code;
    }

    public String description() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Permission)) {
            return false;
        }
        Permission that = (Permission) o;
        return Objects.equals(code, that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code);
    }

    @Override
    public String toString() {
        return "Permission{" + code + "}";
    }

}