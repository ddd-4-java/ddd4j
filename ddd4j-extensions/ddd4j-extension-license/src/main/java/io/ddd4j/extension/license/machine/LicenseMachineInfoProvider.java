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
package io.ddd4j.extension.license.machine;

import java.util.Set;

/**
 * 当前运行环境机器信息提供策略。
 *
 * <p>业务可注入云平台实例 ID、容器节点标识或可信硬件序列号，替代默认系统采集逻辑。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface LicenseMachineInfoProvider {

    /**
     * 获取当前机器的 IP 地址。
     *
     * @return IP 地址集合
     */
    Set<String> ipAddresses();

    /**
     * 获取当前机器的 MAC 地址。
     *
     * @return MAC 地址集合
     */
    Set<String> macAddresses();

    /**
     * 获取机器序列号。
     *
     * @return 机器序列号
     */
    String serialNumber();
}
