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
        @ExcelProperty("用户ID")
        @ColumnWidth(15)
        private Long id;

        @ExcelProperty("姓名")
        @ColumnWidth(20)
        private String name;

        @ExcelProperty(value = "生日", converter = LocalDateConverter.class)
        @ColumnWidth(20)
        private LocalDate birthday;

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
