/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
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
