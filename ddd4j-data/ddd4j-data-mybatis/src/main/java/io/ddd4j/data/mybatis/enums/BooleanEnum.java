package io.ddd4j.data.mybatis.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public enum BooleanEnum implements IEnum<Integer> {

    IS_FALSE(false, "否"),
    IS_TRUE(true, "是");

    private boolean is;

    private String nameCn;

    BooleanEnum(boolean is, String nameCn) {
        this.is = is;
        this.nameCn = nameCn;
    }

    public boolean isIs() {
        return is;
    }

    public void setIs(boolean is) {
        this.is = is;
    }

    public String getNameCn() {
        return nameCn;
    }

    public void setNameCn(String nameCn) {
        this.nameCn = nameCn;
    }

    // 添加一个静态方法来根据值获取枚举
    public static BooleanEnum valueOf(int value) {
        for (BooleanEnum booleanEnum : BooleanEnum.values()) {
            if (booleanEnum.isIs() && 1 == value) {
                return booleanEnum;
            } else if (!booleanEnum.isIs() && 0 == value) {
                return booleanEnum;
            }
        }
        throw new IllegalArgumentException("No enum constant " + BooleanEnum.class.getName() + "." + value);
    }

    @Override
    public Integer getValue() {
        return is ? 1 : 0;
    }

}
