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
 * Servlet \u8bf7\u6c42\u5305\u88c5\u6269\u5c55\u70b9\u3002
 *
 *  <p>\u63d2\u4ef6\u5b9e\u73b0\u8be5\u63a5\u53e3\uff0c\u4e3a\u4e3b\u63a7\u63d0\u4f9b\u5bf9 HTTP \u8bf7\u6c42\u7684\u6539\u5199\uff08wrap\uff09\u80fd\u529b\u3002
 * @since 2.0.x
 */
public interface ServletRequestExtensionPoint extends ExtensionPoint {

    /**
     * \u6309\u771f\u5b9e\u4e1a\u52a1\u53c2\u6570\u6539\u5199 HTTP \u8bf7\u6c42\u3002
     * @param request HTTP \u8bf7\u6c42
     * @param realParams \u771f\u5b9e\u4e1a\u52a1\u53c2\u6570\u96c6\u5408
     * @return \u6539\u5199\u540e\u7684\u8bf7\u6c42\u63cf\u8ff0
     */
    String wrap(HttpServletRequest request, Map<String, Object> realParams);

    /**
     * \u6539\u5199 {@link RequestEntity} \u5f62\u5f0f\u7684\u8bf7\u6c42\u3002
     * @param <T> \u8bf7\u6c42\u4f53\u7c7b\u578b
     * @param requestEntity \u539f\u59cb\u8bf7\u6c42\u5b9e\u4f53
     * @return \u6539\u5199\u540e\u7684\u8bf7\u6c42\u5b9e\u4f53
     */
    <T> RequestEntity<T> wrap(RequestEntity<T> requestEntity);

}
