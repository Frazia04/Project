package de.rptu.cs.exclaim.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;

/**
 * Whenever we access information about the currently logged-in user, then we automatically start a database transaction
 * bound to the current http request (see {@link AccessChecker} calls {@link #startTransaction(ServletRequest)}).
 * <p>
 * We make two attempts to commit such an automatic database transaction (both in {@link SecurityConfig}):
 * <ul>
 * <li>{@link CompleteAutomaticTransactionFilter}: in a {@code finally}-block of a post filter, thus always executed
 * after rendering the view.
 * <li>{@link CompleteAutomaticTransactionHandlerInterceptor}: as a {@link HandlerInterceptor}, thus executed before
 * rendering the view, unless there is some sort of exception. It is important to commit the transaction before
 * rendering the view such that we can better handle the error if the transaction commit fails.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@Slf4j
@RequiredArgsConstructor
public class AutomaticDatabaseTransaction {
    private static final String REQUEST_ATTRIBUTE_NAME_TRANSACTION = AutomaticDatabaseTransaction.class.getName() + ".TRANSACTION";
    private static final String REQUEST_ATTRIBUTE_NAME_FILTER = AutomaticDatabaseTransaction.class.getName() + ".FILTER";

    private final PlatformTransactionManager transactionManager;
    private final TransactionDefinition transactionDefinition;

    public void startTransaction(ServletRequest request) {
        if (Boolean.TRUE.equals(request.getAttribute(REQUEST_ATTRIBUTE_NAME_FILTER))) {
            if (request.getAttribute(REQUEST_ATTRIBUTE_NAME_TRANSACTION) == null) {
                log.debug("Starting automatic database transaction");
                request.setAttribute(
                    REQUEST_ATTRIBUTE_NAME_TRANSACTION,
                    transactionManager.getTransaction(transactionDefinition)
                );
            } else {
                log.debug("Automatic database transaction already exists");
            }
        } else {
            log.warn(
                "Not starting automatic database transaction since there is no filter on the current request to commit the transaction",
                new Exception("Exception to provide the stack trace")
            );
        }
    }

    private void completeTransaction(ServletRequest request) {
        Object attribute = request.getAttribute(REQUEST_ATTRIBUTE_NAME_TRANSACTION);
        request.removeAttribute(REQUEST_ATTRIBUTE_NAME_TRANSACTION);
        if (attribute instanceof TransactionStatus transactionStatus && !transactionStatus.isCompleted()) {
            try {
                log.debug("Completing automatic database transaction");
                transactionManager.commit(transactionStatus);
            } catch (UnexpectedRollbackException ignored) {
                // Inner transaction requested a rollback
            }
        }
    }

    // Do not automatically insert the CompleteAutomaticTransactionFilter in security filter chains.
    // See https://github.com/spring-projects/spring-boot/issues/16500
    @Bean
    public FilterRegistrationBean<CompleteAutomaticTransactionFilter> completeAutomaticTransactionFilterRegistration(
        CompleteAutomaticTransactionFilter filter
    ) {
        FilterRegistrationBean<CompleteAutomaticTransactionFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Component
    @RequiredArgsConstructor
    public static class CompleteAutomaticTransactionFilter extends OncePerRequestFilter {
        private final AutomaticDatabaseTransaction automaticDatabaseTransaction;

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
            request.setAttribute(REQUEST_ATTRIBUTE_NAME_FILTER, true);

            try {
                filterChain.doFilter(request, response);
            } finally {
                automaticDatabaseTransaction.completeTransaction(request);
            }
        }
    }

    @Component
    @RequiredArgsConstructor
    public static class CompleteAutomaticTransactionHandlerInterceptor implements HandlerInterceptor {
        private final AutomaticDatabaseTransaction automaticDatabaseTransaction;

        @Override
        public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable ModelAndView modelAndView) {
            automaticDatabaseTransaction.completeTransaction(request);
        }
    }
}
