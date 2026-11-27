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
package io.ddd4j.auth.satoken;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 临时 Token 实体类，封装认证过程中的临时凭证信息。
 *
 * <p>包含认证方式、用户身份信息（OpenId/UnionId/LoginId）、
 * 辅助信息（昵称/头像/手机/邮箱等）、请求来源信息（客户端/设备/IP 等）。
 *
 * <p>用于 {@code SaMixCheckLogin} 注解流程中的临时 Token 解析与校验。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Accessors(chain = true)
@Data
public class SaTempToken implements Serializable {

    /**
     * \u8ba4\u8bc1\u65b9\u5f0f
     *  - password\uff0c\u5bc6\u7801\u767b\u5f55\uff1a\u7528\u6237\u8f93\u5165\u8d26\u53f7\u548c\u5bc6\u7801
     *  - qrcode\uff0c\u626b\u7801\u767b\u5f55\uff1a\u7528\u6237\u626b\u63cf\u4e8c\u7ef4\u7801\u767b\u5f55
     *  - sms\uff0c\u77ed\u4fe1\u767b\u5f55\uff1a\u7528\u6237\u8f93\u5165\u624b\u673a\u53f7\u548c\u9a8c\u8bc1\u7801\u767b\u5f55
     *  - face\uff0c\u4eba\u8138\u767b\u5f55\uff1a\u7528\u6237\u4eba\u8138\u8bc6\u522b\u767b\u5f55
     *  - dingtalk-ma\uff0c\u9489\u9489\u5c0f\u7a0b\u5e8f\u767b\u5f55\uff1a\u7528\u6237\u5728\u9489\u9489\u5c0f\u7a0b\u5e8f\u5185\u767b\u5f55
     *  - dingtalk-scancode\uff0c\u9489\u9489\u626b\u7801\u767b\u5f55\uff1a\u7528\u6237\u4f7f\u7528\u9489\u9489\u626b\u7801\u767b\u5f55
     *  - dingtalk-tmpcode\uff0c\u9489\u9489\u4e34\u65f6\u7801\u767b\u5f55\uff1a\u7528\u6237\u5728\u9489\u9489\u5ba2\u6237\u7aef/App\u5185\u514d\u5bc6\u767b\u5f55
     *  - wx-ma\uff0c\u5fae\u4fe1\u5c0f\u7a0b\u5e8f\u767b\u5f55\uff1a\u7528\u6237\u5728\u5fae\u4fe1\u5c0f\u7a0b\u5e8f\u5185\u767b\u5f55
     *  - wx-mp\uff0c\u5fae\u4fe1\u516c\u4f17\u5e73\u53f0\u767b\u5f55\uff1a\u7528\u6237\u5728\u5fae\u4fe1\u516c\u4f17\u53f7\u5185\u767b\u5f55
     *  - zheliban\uff0c\u6d59\u91cc\u529e\u767b\u5f55\uff1a\u7528\u6237\u5728\u6d59\u91cc\u529e\u5185\u767b\u5f55\uff0c\u5982\uff1a\u5b66\u5728\u6d59\u6c5f\u3001\u5b66\u5728xx\u7cfb\u5217
     *  - app\uff0c\u5e94\u7528\u514d\u767b\uff1a\u901a\u8fc7appKey\u548cappSecret\u8fdb\u884c\u514d\u767b
     *  - cas\uff0cCas\u767b\u5f55\uff1a\u901a\u8fc7\u6807\u51c6\u7684JWT\u767b\u5f55
     *  - jwt\uff0cJWT\u767b\u5f55\uff1a\u901a\u8fc7\u6807\u51c6\u7684JWT\u767b\u5f55
     *  - pac4j\uff0c\u4e09\u65b9\u767b\u5f55\uff1a\u57fa\u4e8ePac4j\u8fdb\u884c\u5404\u7c7b\u4e09\u65b9\u767b\u5f55
     */
    private String authType;

    //==============================认证授权信息====================================

    /**
     * 用户 OpenId
     * 说明：openid 是用户在某一 client 下的唯一标识，其有如下特点：
     * - 一个用户在同一个 client 下，openid 是固定的，每次请求都会返回相同的值。
     * - 一个用户在不同的 client 下，openid 是不同的，会返回不同的值。
     */
    private String openid;
    /**
     * 用户 UnionId
     * 说明：UnionId 的特点与 OpenId 几乎一致：同一用户在不同 client 里的 UnionId 值是不同的，除非这些应用属于同一主体。
     * 例如：甲公司申请了应用A、应用B、应用C，乙公司申请了应用D、应用F，那么用户张三：
     * - 在应用 A、B、C 里的 UnionId 值一致。
     * - 在应用 D、F 里的 UnionId 值一致。
     * - 在应用 A 和 应用 D 之间，UnionId 值不一致。
     */
    private String unionId;
    /**
     * 用户 loginId（一般是账号Id）
     */
    private String loginId;
    /**
     * 用户登录时间
     */
    private Long loginTime;

    //==============================辅助信息====================================

    /**
     * 别名（昵称）
     */
    private String nickname;
    /**
     * 头像：图片路径或图标样式
     */
    private String avatar;
    /**
     * 手机号码
     */
    private String phone;
    /**
     * 电子邮箱
     */
    private String email;
    /**
     * 性别（0：未知项、1：男、2：女）
     */
    private Integer gender;
    /**
     * 出生日期
     */
    private Long birthday;
    /**
     * 用户年龄
     */
    private Integer age;
    /**
     * 用户位置：常驻国家/地区编码
     */
    private String regionCode;
    /**
     * 用户位置：常驻国家/地区名称
     */
    private String country;
    /**
     * 用户位置：常驻省份
     */
    private String province;
    /**
     * 用户位置：常驻城市
     */
    private String city;
    /**
     * 用户位置：常驻区域
     */
    private String area;
    /**
     * 用户位置：常驻地经度
     */
    private Double longitude;
    /**
     * 用户位置：常驻地纬度
     */
    private Double latitude;
    /**
     * 官方语言
     */
    private String lang;
    /**
     * 时区
     */
    private String zone;

    //==============================请求来源====================================

    /**
     * 此次登录的客户端ID
     */
    private String appId;
    /**
     * 此次登录的客户端渠道编码
     */
    private String appChannel;
    /**
     * 此次登录的客户端版本号
     */
    private String appVersion;
    /**
     * 此次登录的请求来源IP地址
     */
    private String ipAddress;
    /**
     * 此次登录的客户端设备类型
     */
    private String deviceType;
    /**
     * 此次登录的客户端设备id
     */
    private String deviceId;
    /**
     * 此次登录的客户端 UserAgent 信息
     */
    private String userAgent;

}
