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
package io.ddd4j.core.api;

import io.ddd4j.core.enums.IEnum;
import lombok.Getter;

/**
 * 常用错误码定义
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Getter
public enum ResultCode implements IEnum<Integer> {
    /**
     * 请求/操作成功（0）
     */
    OK(0, "请求/操作成功"),
    /**
     * 请求/操作成功（200，HTTP 标准状态码）
     */
    SUCCESS(200, "请求/操作成功"),
    /**
     * 请求成功但服务异常
     */
    FAIL(1, "请求成功但是服务异常"),
    /**
     * 请求异常（400）
     */
    BAD_REQUEST(400, "请求异常"),
    /**
     * 未登录或 token 已经失效（401）
     */
    UNAUTHORIZED(401, "未登录或token已经失效"),
    /**
     * 没有权限（403）
     */
    FORBIDDEN(403, "没有权限"),
    /**
     * 没有权限，缺少 AccessToken
     */
    NO_ACCESS_TOKEN(403001, "没有权限，缺少AccessToken"),
    /**
     * 服务器异常（500）
     */
    SERVER_ERROR(500, "服务器异常"),
    /**
     * 参数校验失败
     */
    PARAMETER_VALIDATION_FAILED(400100, "参数校验失败"),
    /**
     * 登录超时，请重新登录
     */
    TIMEOUT(60001, "登录超时，请重新登录"),
    /**
     * Session 不能为空
     */
    NO_SESSION(60002, "session不能为空"),
    /**
     * 请先登录
     */
    LOGIN_FIRST(60003, "请先登录"),
    ;

    private final Integer code;
    private final String desc;

    private ResultCode(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据错误码获取对应的错误描述。
     *
     * @param code 错误码
     * @return 错误描述，未找到返回空字符串
     */
    public static String getDescByCode(Integer code) {
        String desc = "";
        ResultCode[] var2 = values();
        int var3 = var2.length;

        for (int var4 = 0; var4 < var3; ++var4) {
            ResultCode codeEnum = var2[var4];
            if (codeEnum.getCode().equals(code)) {
                desc = codeEnum.getDesc();
                break;
            }
        }

        return desc;
    }

    /**
     * 根据错误码获取对应的枚举实例。
     *
     * @param code 错误码
     * @return 对应的 ResultCode 枚举，未找到返回 null
     */
    public static ResultCode getByCode(Integer code) {
        ResultCode resultCode = null;
        ResultCode[] var2 = values();
        int var3 = var2.length;

        for (int var4 = 0; var4 < var3; ++var4) {
            ResultCode codeEnum = var2[var4];
            if (codeEnum.getCode().equals(code)) {
                resultCode = codeEnum;
                break;
            }
        }

        return resultCode;
    }
}
