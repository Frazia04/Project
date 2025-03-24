package de.rptu.cs.exclaim.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Constraint for strings that shall be parsed as Spring Expression Language (SpEL) expression.
 */
@Documented
@Constraint(validatedBy = ValidSpELExpressionValidator.class)
@Target({FIELD, TYPE})
@Retention(RUNTIME)
public @interface ValidSpELExpression {

    String message() default "Not a valid SpEL expression";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
