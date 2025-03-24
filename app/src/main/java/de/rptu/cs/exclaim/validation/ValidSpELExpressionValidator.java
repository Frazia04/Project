package de.rptu.cs.exclaim.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * Constraint validator for {@link ValidSpELExpression} that parses Spring Expression Language (SpEL) expressions.
 */
public class ValidSpELExpressionValidator implements ConstraintValidator<ValidSpELExpression, Object> {
    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (!(value instanceof String expression)) {
            return false;
        }

        try {
            new SpelExpressionParser().parseExpression(expression);
        } catch (Exception e) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                context.getDefaultConstraintMessageTemplate() +
                    ": " + e.getMessage()
            ).addConstraintViolation();
            return false;
        }

        return true;
    }
}
