package io.ddd4j.data.enums;

import com.baomidou.mybatisplus.annotation.IEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public enum BooleanEnum implements IEnum<Integer> {

    IS_FALSE(false, "否"),
    IS_TRUE(true, "是");

    @Setter
    private boolean is;

    @Setter
    private String nameCn;

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
