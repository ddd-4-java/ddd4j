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
package io.ddd4j.extension.license;

import lombok.Data;

import java.io.Serializable;
import java.util.Objects;

/**
 * License 附加校验信息模型。
 *
 * <p>承载除 TrueLicense 标准字段（subject/有效期等）之外的自定义校验维度，
 * 例如允许部署的服务器 IP、MAC 地址、硬件 SN 码等，用于实现"机器绑定"。
 *
 * <p>向后兼容：所有字段默认为 null，{@code CustomLicenseManager.validate} 中
 * 仅对非 null 字段做比对，null 字段视为不限制，因此旧证书（extra 为 null）仍可校验通过。
 *
 * <p>实现 {@code Serializable}，便于随 {@link LicenseInfo} 一同进入 CacheKit 远程缓存。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class LicenseExtraModel implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 允许部署的 IP 地址（精确匹配），为 null 表示不校验
     */
    private String ip;
    /**
     * 允许部署的 MAC 地址，为 null 表示不校验
     */
    private String mac;
    /**
     * 允许部署的服务器 SN 码，为 null 表示不校验
     */
    private String sn;
    /**
     * 备注 / 项目信息等业务自定义内容
     */
    private String remark;

    /**
     * 判断是否声明了任何校验维度（全 null 表示无附加约束）。
     *
     * @return true 表示存在至少一个非 null 的校验字段
     */
    public boolean hasAnyConstraint() {
        return Objects.nonNull(ip) || Objects.nonNull(mac) || Objects.nonNull(sn);
    }
}
