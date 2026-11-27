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
import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * Servlet \u54cd\u5e94\u62c6\u5305\u6269\u5c55\u70b9\u3002
 *
 *  <p>\u63d2\u4ef6\u5b9e\u73b0\u8be5\u63a5\u53e3\uff0c\u4e3a\u4e3b\u63a7\u63d0\u4f9b\u5bf9 HTTP \u54cd\u5e94\u7684\u8fd8\u539f\uff08dewrap\uff09\u80fd\u529b\u3002
 * @since 2.0.x
 */
public interface ServletResponseExtensionPoint extends ExtensionPoint {

    /**
     * \u6309\u771f\u5b9e\u4e1a\u52a1\u53c2\u6570\u8fd8\u539f HTTP \u54cd\u5e94\u3002
     * @param response HTTP \u54cd\u5e94
     * @param realParams \u771f\u5b9e\u4e1a\u52a1\u53c2\u6570\u96c6\u5408
     * @return \u8fd8\u539f\u540e\u7684\u54cd\u5e94\u63cf\u8ff0
     */
    String dewrap(HttpServletResponse response, Map<String, Object> realParams);

    /**
     * \u8fd8\u539f {@link ResponseEntity} \u5f62\u5f0f\u7684\u54cd\u5e94\u3002
     * @param <T> \u54cd\u5e94\u4f53\u7c7b\u578b
     * @param responseEntity \u539f\u59cb\u54cd\u5e94\u5b9e\u4f53
     * @return \u8fd8\u539f\u540e\u7684\u54cd\u5e94\u5b9e\u4f53
     */
    <T> ResponseEntity<T> dewrap(ResponseEntity<T> responseEntity);

}
