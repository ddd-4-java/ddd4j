package io.ddd4j.data.external.region;

import lombok.extern.slf4j.Slf4j;

/**
 * http://www.mca.gov.cn/article/sj/xzqh/1980/201903/201903011447.html
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public enum ProvinceEnum {

    /** \u5317\u4eac\u5e02\uff08110000\uff09\u3002 */
    P110000("110000", "北京市"),
    /** \u5929\u6d25\u5e02\uff08120000\uff09\u3002 */
    P120000("120000", "天津市"),
    /** \u6cb3\u5317\u7701\uff08130000\uff09\u3002 */
    P130000("130000", "河北省"),
    /** \u5c71\u897f\u7701\uff08140000\uff09\u3002 */
    P140000("140000", "山西省"),
    /** \u5185\u8499\u53e4\u81ea\u6cbb\u533a\uff08150000\uff09\u3002 */
    P150000("150000", "内蒙古自治区"),
    /** \u8fbd\u5b81\u7701\uff08210000\uff09\u3002 */
    P210000("210000", "辽宁省"),
    /** \u5409\u6797\u7701\uff08220000\uff09\u3002 */
    P220000("220000", "吉林省"),
    /** \u9ed1\u9f99\u6c5f\u7701\uff08230000\uff09\u3002 */
    P230000("230000", "黑龙江省"),
    /** \u4e0a\u6d77\u5e02\uff08310000\uff09\u3002 */
    P310000("310000", "上海市"),
    /** \u6c5f\u82cf\u7701\uff08320000\uff09\u3002 */
    P320000("320000", "江苏省"),
    /** \u6d59\u6c5f\u7701\uff08330000\uff09\u3002 */
    P330000("330000", "浙江省"),
    /** \u5b89\u5fbd\u7701\uff08340000\uff09\u3002 */
    P340000("340000", "安徽省"),
    /** \u798f\u5efa\u7701\uff08350000\uff09\u3002 */
    P350000("350000", "福建省"),
    /** \u6c5f\u897f\u7701\uff08360000\uff09\u3002 */
    P360000("360000", "江西省"),
    /** \u5c71\u4e1c\u7701\uff08370000\uff09\u3002 */
    P370000("370000", "山东省"),
    /** \u6cb3\u5357\u7701\uff08410000\uff09\u3002 */
    P410000("410000", "河南省"),
    /** \u6e56\u5317\u7701\uff08420000\uff09\u3002 */
    P420000("420000", "湖北省"),
    /** \u6e56\u5357\u7701\uff08430000\uff09\u3002 */
    P430000("430000", "湖南省"),
    /** \u5e7f\u4e1c\u7701\uff08440000\uff09\u3002 */
    P440000("440000", "广东省"),
    /** \u5e7f\u897f\u58ee\u65cf\u81ea\u6cbb\u533a\uff08450000\uff09\u3002 */
    P450000("450000", "广西壮族自治区"),
    /** \u6d77\u5357\u7701\uff08460000\uff09\u3002 */
    P460000("460000", "海南省"),
    /** \u91cd\u5e86\u5e02\uff08500000\uff09\u3002 */
    P500000("500000", "重庆市"),
    /** \u56db\u5ddd\u7701\uff08510000\uff09\u3002 */
    P510000("510000", "四川省"),
    /** \u8d35\u5dde\u7701\uff08520000\uff09\u3002 */
    P520000("520000", "贵州省"),
    /** \u4e91\u5357\u7701\uff08530000\uff09\u3002 */
    P530000("530000", "云南省"),
    /** \u897f\u85cf\u81ea\u6cbb\u533a\uff08540000\uff09\u3002 */
    P540000("540000", "西藏自治区"),
    /** \u9655\u897f\u7701\uff08610000\uff09\u3002 */
    P610000("610000", "陕西省"),
    /** \u7518\u8083\u7701\uff08620000\uff09\u3002 */
    P620000("620000", "甘肃省"),
    /** \u9752\u6d77\u7701\uff08630000\uff09\u3002 */
    P630000("630000", "青海省"),
    /** \u5b81\u590f\u56de\u65cf\u81ea\u6cbb\u533a\uff08640000\uff09\u3002 */
    P640000("640000", "宁夏回族自治区"),
    /** \u65b0\u7586\u7ef4\u543e\u5c14\u81ea\u6cbb\u533a\uff08650000\uff09\u3002 */
    P650000("650000", "新疆维吾尔自治区"),
    /** \u53f0\u6e7e\u7701\uff08710000\uff09\u3002 */
    P710000("710000", "台湾省"),
    /** \u9999\u6e2f\u7279\u522b\u884c\u653f\u533a\uff08810000\uff09\u3002 */
    P810000("810000", "香港特别行政区"),
    /** \u6fb3\u95e8\u7279\u522b\u884c\u653f\u533a\uff08820000\uff09\u3002 */
    P820000("820000", "澳门特别行政区"),

    /** \u672a\u77e5\u884c\u653f\u533a\uff08999999\uff09\u3002 */
    UK("999999", "未知行政区"),

    ;

    /**
     * 行政区划代码
     */
    private String code;
    /**
     * 单位名称
     */
    private String cname;

    /**
     * 构造函数
     *
     * @param code  行政区划代码
     * @param cname 省份名称
     */
    private ProvinceEnum(String code, String cname) {
        this.code = code;
        this.cname = cname;
    }

    /**
     * 根据行政区划代码获取省份枚举
     *
     * @param code 行政区划代码
     * @return 省份枚举
     */
    public static ProvinceEnum getByCode(String code) {
        for (ProvinceEnum region : ProvinceEnum.values()) {
            if (region.getCode().equalsIgnoreCase(code)) {
                return region;
            }
        }
        log.error("Cannot found ProvinceEnum with code '{}'.", code);
        return ProvinceEnum.UK;
    }

    /**
     * 根据中文名称获取省份枚举
     *
     * @param cn_name 中文名称
     * @return 省份枚举
     */
    public static ProvinceEnum getByCnName(String cn_name) {
        for (ProvinceEnum region : ProvinceEnum.values()) {
            if (region.getCname().equalsIgnoreCase(cn_name)) {
                return region;
            }
        }
        log.error("Cannot found ProvinceEnum with cn_name '{}'.", cn_name);
        return ProvinceEnum.UK;
    }

    /**
     * \u8fd4\u56de\u884c\u653f\u533a\u5212\u4ee3\u7801\u3002
     * @return \u884c\u653f\u533a\u5212\u4ee3\u7801
     */
    public String getCode() {
        return code;
    }

    /**
     * \u8fd4\u56de\u7701\u4efd\u4e2d\u6587\u540d\u79f0\u3002
     * @return \u7701\u4efd\u4e2d\u6587\u540d\u79f0
     */
    public String getCname() {
        return cname;
    }

}
