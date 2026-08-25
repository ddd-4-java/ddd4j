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
package io.ddd4j.web.webflux.config;

import lombok.Data;

/**
 * 软件服务供应商信息
 */
@Data
public class ServerVendorProperties {

    /**
     * 省份
     */
    private String province;

    /**
     * 城市
     */
    private String city;

    /**
     * 描述
     */
    private String desc;
    /**
     * 版权信息
     */
    private String copyright;

    /**
     * IPC
     */
    private String ipcLicense;
    /**
     * 企业名称
     */
    private String company;
    /**
     * 地址
     */
    private String addr;
    /**
     * 电话
     */
    private String tel;
    /**
     * 电子邮箱
     */
    private String email;
    /**
     * 传真
     */
    private String fax;
    /**
     * 系统名称
     */
    private String title;

    @Override
    public String toString() {
        return "ServiceVendor{" + "province='" + province + '\'' + ", city='" + city + '\'' + ", desc='" + desc + '\''
                + '}';
    }
}
