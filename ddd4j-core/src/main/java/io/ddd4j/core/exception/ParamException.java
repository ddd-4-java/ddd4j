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
package io.ddd4j.core.exception;

import io.ddd4j.core.api.ApiCode;
import io.ddd4j.core.api.CustomApiCode;


/**
 * 参数校验异常。
 * <p>
 * 当请求参数不符合预期时抛出，由统一异常处理层捕获后返回友好提示。
 * 继承 {@link BizRuntimeException}，支持国际化消息和业务错误码。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class ParamException extends BizRuntimeException {

    public ParamException(Integer code, String message) {
        super(code, message);
    }

    public ParamException(Integer code, String i18nCode, String message) {
        super(code, i18nCode, message);
    }

    public ParamException(Integer code, String i18nCode, Object[] args, String message) {
        super(code, i18nCode, args, message);
    }

    public ParamException(String message) {
        super(message);
    }

    public ParamException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 按标准业务码和国际化键创建异常。
     *
     * @param code 标准业务错误码
     * @param i18nCode 国际化资源键
     */
    public ParamException(ApiCode code, String i18nCode) {
        this((CustomApiCode) code, i18nCode);
    }

    public ParamException(CustomApiCode code, String i18nCode) {
        super(code, i18nCode);
    }

    public ParamException(Integer code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public ParamException(Integer code, String i18nCode, String defMsg, Throwable cause) {
        super(code, i18nCode, defMsg, cause);
    }

    public ParamException(CustomApiCode code) {
        super(code);
    }

}
