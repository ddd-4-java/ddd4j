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


import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import io.ddd4j.web.validation.constraints.PhoneNumber;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * 数据校验注解实现类
 *
 * @author hiwepy
 * @since 2021-03-08
 */
public class PhoneValueValidator implements ConstraintValidator<PhoneNumber, String> {

    private static final PhoneNumberUtil PHONE_NUMBER_UTIL = PhoneNumberUtil.getInstance();

    private PhoneNumber phoneValue;

    @Override
    public void initialize(PhoneNumber annotation) {
        this.phoneValue = annotation;
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {

        Phonenumber.PhoneNumber referencePhonenumber = new Phonenumber.PhoneNumber();
        try {

            referencePhonenumber = PHONE_NUMBER_UTIL.parse(value, phoneValue.lang());
            boolean flag = PHONE_NUMBER_UTIL.isPossibleNumber(referencePhonenumber);
            if (!flag) {
                constraintValidatorContext.disableDefaultConstraintViolation();
                constraintValidatorContext.buildConstraintViolationWithTemplate(phoneValue.message())
                        .addConstraintViolation();
            }
            return flag;
        } catch (NumberParseException e) {
            constraintValidatorContext.disableDefaultConstraintViolation();
            constraintValidatorContext.buildConstraintViolationWithTemplate(phoneValue.message())
                    .addConstraintViolation();
            return false;
        }
    }

}
