package de.rptu.cs.exclaim.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.lang.Nullable;

/**
 * Constraint validator for {@link SpELAssert} that evaluates Spring Expression (SpEL).
 */
public class SpELAssertValidator implements ConstraintValidator<SpELAssert, Object>, BeanFactoryAware {
    @Nullable
    private BeanFactory beanFactory;

    @Nullable
    private Expression expression;

    private boolean checkNull;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public void initialize(SpELAssert constraintAnnotation) {
        ExpressionParser parser = new SpelExpressionParser();
        expression = parser.parseExpression(constraintAnnotation.value());
        checkNull = constraintAnnotation.checkNull();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (expression == null) {
            throw new IllegalStateException("isValid must be called after initialize");
        }

        if (value == null && !checkNull) {
            return true;
        }

        StandardEvaluationContext evaluationContext = new StandardEvaluationContext();
        evaluationContext.setRootObject(value);
        if (beanFactory != null) {
            evaluationContext.setBeanResolver(new BeanFactoryResolver(beanFactory));
        }

        Boolean result = expression.getValue(evaluationContext, value, Boolean.class);
        return result != null && result;
    }
}
