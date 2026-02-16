package az.magusframework.components.lib.autoconfigure;

import az.magusframework.components.lib.observability.core.metrics.MetricsRecorder;
import az.magusframework.components.lib.servlet.body.JsonMagusIdsExtractor;
import az.magusframework.components.lib.servlet.body.NoopRequestBodyExtractor;
import az.magusframework.components.lib.servlet.body.RequestBodyExtractor;
import az.magusframework.components.lib.servlet.filter.ObservabilityHttpFilter;
import az.magusframework.components.lib.servlet.route.DefaultRouteResolver;
import az.magusframework.components.lib.servlet.route.HttpRouteResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;


@AutoConfiguration
@EnableConfigurationProperties(ObservabilityProperties.class)
@ConditionalOnClass({ Filter.class, ObservabilityHttpFilter.class })
@ConditionalOnProperty(prefix = "observability.http", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ObservabilityServletAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(HttpRouteResolver.class)
    public HttpRouteResolver httpRouteResolver() {
        return new DefaultRouteResolver();
    }

    @Bean
    @ConditionalOnMissingBean(RequestBodyExtractor.class)
    @ConditionalOnClass(name = "com.fasterxml.jackson.databind.ObjectMapper")
    public RequestBodyExtractor requestBodyExtractor(ObservabilityProperties props) {
        if (props.getHttp().isExtractMagusIdsFromJsonBody()) {
            return new JsonMagusIdsExtractor(props.getHttp().getMaxBodyBytesToParse());
        }
        return new NoopRequestBodyExtractor();
    }

    @Bean
    @ConditionalOnMissingBean(RequestBodyExtractor.class)
    public RequestBodyExtractor noopBodyExtractorFallback() {
        return new NoopRequestBodyExtractor();
    }

    @Bean
    @ConditionalOnMissingBean(ObservabilityHttpFilter.class)
    public ObservabilityHttpFilter observabilityHttpFilter(
            MetricsRecorder metricsRecorder,
            HttpRouteResolver routeResolver,
            RequestBodyExtractor extractor,
            ObservabilityProperties props
    ) {
        var svc = props.getService();
        var http = props.getHttp();

        return new ObservabilityHttpFilter(
                metricsRecorder,
                routeResolver,
                extractor,
                svc.getAppName(),
                svc.getServiceName(),
                svc.getModule(),
                svc.getComponent(),
                svc.getEnv(),
                http.getMaxCachedBodyBytes()
        );
    }

    @Bean
    @ConditionalOnMissingBean(name = "observabilityHttpFilterRegistration")
    public FilterRegistrationBean<ObservabilityHttpFilter> observabilityHttpFilterRegistration(
            ObservabilityHttpFilter filter,
            ObservabilityProperties props
    ) {
        FilterRegistrationBean<ObservabilityHttpFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(filter);
        reg.setOrder(props.getHttp().getFilterOrder());
        reg.addUrlPatterns("/*");
        reg.setName("observabilityHttpFilter");
        return reg;
    }
}
