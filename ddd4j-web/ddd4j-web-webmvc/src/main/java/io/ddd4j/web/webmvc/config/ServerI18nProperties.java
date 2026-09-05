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
package io.ddd4j.web.webmvc.config;

import lombok.Data;

/**
 * 软件服务国际化信息
 */
@Data
public class ServerI18nProperties {

    /**
     * 是否启用国际化
     */
    private boolean enabled;
    /**
     * 根据环境是否给客户端抛出未知具体异常信息
     */
    private boolean printErrorDetail;

}
