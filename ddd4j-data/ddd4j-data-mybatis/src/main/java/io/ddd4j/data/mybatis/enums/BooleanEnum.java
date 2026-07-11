package io.ddd4j.data.mybatis.enums;

import lombok.Getter;

/**
 * 布尔值枚举，用于数据库中布尔字段与整型字段的映射。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Getter
public enum BooleanEnum {

    IS_FALSE(false, "否"),
    IS_TRUE(true, "是");

    private final boolean is;
    private final String nameCn;

    BooleanEnum(boolean is, String nameCn) {
        this.is = is;
        this.nameCn = nameCn;
    }

    public static BooleanEnum valueOf(int value) {
        for (BooleanEnum booleanEnum : BooleanEnum.values()) {
            if (booleanEnum.getValue() == value) {
                return booleanEnum;
            }
        }
        throw new IllegalArgumentException("No enum constant " + BooleanEnum.class.getName() + "." + value);
    }

    public int getValue() {
        return is ? 1 : 0;
    }
}
