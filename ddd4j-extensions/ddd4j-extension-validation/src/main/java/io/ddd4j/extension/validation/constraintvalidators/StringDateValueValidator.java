package io.ddd4j.extension.validation.constraintvalidators;

import io.ddd4j.extension.validation.constraints.StringDateValue;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;

/**
 * 字符串日期格式校验器
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2021-03-08
 */
@Slf4j
public class StringDateValueValidator implements ConstraintValidator<StringDateValue, String> {

    private StringDateValue dateValue;

    @Override
    public void initialize(StringDateValue annotation) {
        this.dateValue = annotation;
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {
        if (!StringUtils.hasText(value)) {
            return true;
        }
        boolean res = false;
        String msg = "";
        String pattern = dateValue.pattern();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
        // 设置lenient为false.
        // 否则SimpleDateFormat会比较宽松地验证日期，比如2007/02/29会被接受，并转换成2007/03/01
        simpleDateFormat.setLenient(false);
        try {
            simpleDateFormat.parse(value);
            res = true;
        } catch (ParseException e) {
            log.error("字符串日期解析出错", e);
            msg = dateValue.message() + "字符串日期格式出错";
        }
        if (!res) { // res为false表明有错误提示输出
            constraintValidatorContext.disableDefaultConstraintViolation();
            constraintValidatorContext.buildConstraintViolationWithTemplate(msg).addConstraintViolation();
        }
        return res;
    }
}
