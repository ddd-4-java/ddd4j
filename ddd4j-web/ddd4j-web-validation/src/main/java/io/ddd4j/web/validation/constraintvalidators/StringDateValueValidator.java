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
package io.ddd4j.web.validation.constraintvalidators;

import io.ddd4j.web.validation.constraints.StringDateValue;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;

/**
 * 字符串日期格式校验器
 *
 * @author hiwepy
 * @since 2021-03-08
 */
public class StringDateValueValidator implements ConstraintValidator<StringDateValue, String> {

    private static Logger logger = LoggerFactory.getLogger(StringDateValueValidator.class);

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
            logger.error("字符串日期解析出错");
            msg = dateValue.message() + "字符串日期格式出错";
        }
        if (res == false) { // res为false表明有错误提示输出
            constraintValidatorContext.disableDefaultConstraintViolation();
            constraintValidatorContext.buildConstraintViolationWithTemplate(msg).addConstraintViolation();
        }
        return res;
    }
}
