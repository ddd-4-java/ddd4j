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
package io.ddd4j.extension.pf4j.point.web;

import org.pf4j.ExtensionPoint;
import org.springframework.http.RequestEntity;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
public interface ServletRequestExtensionPoint extends ExtensionPoint {

    /**
     * 将 HTTP 请求包装为扩展点可处理的载体。
     *
     * @param request    HTTP 请求对象
     * @param realParams 实际业务参数，包装过程中可补充写入
     * @return 包装后的请求载体
     */
    String wrap(HttpServletRequest request, Map<String, Object> realParams);

    /**
     * 将请求实体包装为扩展点可处理的载体。
     *
     * @param requestEntity 原始请求实体
     * @param <T>           请求体类型
     * @return 包装后的请求实体
     */
    <T> RequestEntity<T> wrap(RequestEntity<T> requestEntity);

}
