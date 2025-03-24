package de.rptu.cs.exclaim.monitoring;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.servlet.jakarta.exporter.MetricsServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PrometheusConfiguration {
    @Bean
    CollectorRegistry metricsRegistry() {
        return new CollectorRegistry(true);
    }

    @Bean
    ServletRegistrationBean<MetricsServlet> registerPrometheusExporterServlet(CollectorRegistry metricsRegistry) {
        return new ServletRegistrationBean<>(new MetricsServlet(metricsRegistry), "/metrics");
    }
}
