package de.rptu.cs.exclaim.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Constraint for validation with Spring Expression Language (SpEL).
 */
@Documented
@Constraint(validatedBy = SpELAssertValidator.class)
@Target({FIELD, TYPE})
@Retention(RUNTIME)
@Repeatable(SpELAssert.List.class)
public @interface SpELAssert {

    String message() default "{de.rptu.cs.exclaim.validation.SpELAssert.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Whether the validation is also applied to null values.
     */
    boolean checkNull() default false;

    /**
     * Validation expression in SpEL.
     *
     * @see <a href="https://docs.spring.io/spring-framework/docs/5.3.x/reference/html/core.html#expressions">
     * Spring Expression Language Documentation</a>
     */
    String value();

    /**
     * Defines several {@code @SpELAssert} constraints on the same element.
     *
     * @see SpELAssert
     */
    @Target({FIELD, TYPE})
    @Retention(RUNTIME)
    @Documented
    public @interface List {
        SpELAssert[] value();
    }
}
