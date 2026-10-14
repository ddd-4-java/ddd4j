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
package io.ddd4j.web.testkit;

import java.util.Map;

/**
 * Web 适配器测试提供的框架无关请求客户端。
 */
public interface WebContractClient {

    /**
     * 发起契约测试请求。
     *
     * @param method HTTP 方法
     * @param path 请求路径
     * @param headers 请求头
     * @param body 请求体
     * @return 响应快照
     */
    WebContractResponse request(String method, String path, Map<String, String> headers, String body);
}
