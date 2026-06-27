package io.ddd4j.extension.validation.constraintvalidators;

import io.ddd4j.extension.validation.constraints.AllowableValues;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 验证值是否在指定范围内
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2021-03-08
 */
public class AllowedValuesValidator implements ConstraintValidator<AllowableValues, String> {

    List<String> allows;
    boolean nullable;

    @Override
    public void initialize(AllowableValues annotation) {
        nullable = annotation.nullable();
        allows = Arrays.asList(StringUtils.tokenizeToStringArray(annotation.allows(), ","));
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (nullable && !StringUtils.hasText(value)) {
            return true;
        }
        return allows.contains(value);
    }
}
