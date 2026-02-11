package az.magusframework.components.lib.autoconfigure;

import az.magusframework.components.lib.servlet.route.HttpRouteResolver;
import az.magusframework.components.lib.spring.webmvc.SpringMvcRouteResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;


@AutoConfiguration
@ConditionalOnClass(SpringMvcRouteResolver.class)
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilitySpringMvcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(HttpRouteResolver.class)
    public HttpRouteResolver springMvcRouteResolver() {
        return new SpringMvcRouteResolver();
    }
}
