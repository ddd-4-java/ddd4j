/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.web.webmvc.config;

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
/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
    public String toString() {
        return "ServiceVendor{" + "province='" + province + '\'' + ", city='" + city + '\'' + ", desc='" + desc + '\''
                + '}';
    }
}
