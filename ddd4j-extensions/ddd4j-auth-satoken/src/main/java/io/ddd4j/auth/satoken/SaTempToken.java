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
     * 认证方式
     * - password，密码登录：用户输入账号和密码
     * - qrcode，扫码登录：用户扫描二维码登录
     * - sms，短信登录：用户输入手机号和验证码登录
     * - face，人脸登录：用户人脸识别登录
     * - dingtalk-ma，钉钉小程序登录：用户在钉钉小程序内登录
     * - dingtalk-scancode，钉钉扫码登录：用户使用钉钉扫码登录
     * - dingtalk-tmpcode，钉钉临时码登录：用户在钉钉客户端/App内免密登录
     * - wx-ma，微信小程序登录：用户在微信小程序内登录
     * - wx-mp，微信公众平台登录：用户在微信公众号内登录
     * - zheliban，浙里办登录：用户在浙里办内登录，如：学在浙江、学在xx系列
     * - app，应用免登：通过appKey和appSecret进行免登
     * - cas，Cas登录：通过标准的JWT登录
     * - jwt，JWT登录：通过标准的JWT登录
     * - pac4j，三方登录：基于Pac4j进行各类三方登录
     *
     * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
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
