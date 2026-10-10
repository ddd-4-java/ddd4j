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

import javax.servlet.http.HttpServletResponse;
import org.pf4j.ExtensionPoint;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
public interface ServletResponseExtensionPoint extends ExtensionPoint {

    /**
     * 将 HTTP 响应还原为调用方可读的结果载体。
     *
     * @param response   HTTP 响应对象
     * @param realParams 实际业务参数，还原过程中可读取
     * @return 还原后的结果载体
     */
    String dewrap(HttpServletResponse response, Map<String, Object> realParams);

    /**
     * 将响应实体还原为调用方可读的结果载体。
     *
     * @param responseEntity 原始响应实体
     * @param <T>            响应体类型
     * @return 还原后的响应实体
     */
    <T> ResponseEntity<T> dewrap(ResponseEntity<T> responseEntity);

}
