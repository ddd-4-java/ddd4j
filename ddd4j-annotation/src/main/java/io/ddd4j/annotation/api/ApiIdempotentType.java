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
package io.ddd4j.annotation.api;

import java.util.NoSuchElementException;

/**
 * 幂等方式
 */
public enum ApiIdempotentType {

    /**
     * 通过请求参数中的token值实现幂等
     */
    TOKEN,
    /**
     * 通过参数值
     */
    ARGS;

    /**
     * 根据字符串名称（忽略大小写）获取对应的幂等方式枚举
     *
     * @param type 幂等方式名称字符串
     * @return 匹配的 ApiIdempotentType 枚举
     * @throws NoSuchElementException 如果未找到匹配的枚举值
     */
    public static ApiIdempotentType valueOfIgnoreCase(String type) {

        for (ApiIdempotentType typeEnum : ApiIdempotentType.values()) {
            if (typeEnum.name().equals(type)) {
                return typeEnum;
            }
        }
        throw new NoSuchElementException("Cannot found AliIdempotentType with type '" + type + "'.");
    }

    /**
     * 比较当前幂等方式是否与指定枚举相同
     *
     * @param type 待比较的幂等方式枚举
     * @return 若相同返回 true，否则返回 false
     */
    public boolean equals(ApiIdempotentType type) {
        return this.compareTo(type) == 0;
    }

    /**
     * 比较当前幂等方式是否与指定字符串表示的类型相同
     *
     * @param type 待比较的幂等方式字符串
     * @return 若相同返回 true，否则返回 false
     */
    public boolean equals(String type) {
        return this.compareTo(ApiIdempotentType.valueOfIgnoreCase(type)) == 0;
    }

}
