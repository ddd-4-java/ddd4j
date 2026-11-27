package io.ddd4j.extension.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentRowHeight;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import io.ddd4j.extension.excel.convert.LocalDateConverter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 测试用 POJO（共享给各测试类）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@NoArgsConstructor
@HeadRowHeight(20)
@ContentRowHeight(15)
public class TestModels {

    /**
     * 用户导出/导入 VO（覆盖常见类型）。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserVO {
        /** \u7528\u4e8e\u6784\u9020\u548c\u5b9a\u4f4d\u6d4b\u8bd5\u8bb0\u5f55\u7684\u4e3b\u952e\u3002 */
        @ExcelProperty("用户ID")
        @ColumnWidth(15)
        private Long id;

        /** \u6d4b\u8bd5\u5bf9\u8c61\u7684\u663e\u793a\u540d\u79f0\u3002 */
        @ExcelProperty("姓名")
        @ColumnWidth(20)
        private String name;

        /** \u7528\u4e8e\u9a8c\u8bc1\u65e5\u671f\u5b57\u6bb5\u5e8f\u5217\u5316\u6216\u6570\u636e\u6620\u5c04\u7684\u751f\u65e5\u6837\u672c\u3002 */
        @ExcelProperty(value = "生日", converter = LocalDateConverter.class)
        @ColumnWidth(20)
        private LocalDate birthday;

        /** \u6d4b\u8bd5\u6301\u4e45\u5316\u6a21\u578b\u4e2d\u7684\u521b\u5efa\u65f6\u95f4\u5b57\u6bb5\u3002 */
        @ExcelProperty("创建时间")
        @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
        @ColumnWidth(25)
        private Date createTime;
    }

    /**
     * 构造测试数据。
     *
     * @param n 行数
     * @return UserVO 列表
     */
    public static List<UserVO> sampleUsers(int n) {
        List<UserVO> list = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) {
            list.add(new UserVO((long) i, "用户" + i,
                    LocalDate.of(1990, 1, 1).plusDays(i),
                    new Date()));
        }
        return list;
    }
}
