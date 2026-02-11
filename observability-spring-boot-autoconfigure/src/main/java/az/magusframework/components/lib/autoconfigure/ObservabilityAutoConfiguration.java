package az.magusframework.components.lib.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@AutoConfiguration
@EnableConfigurationProperties({
        ObservabilityProperties.class,
        ObservabilityOtelProperties.class
})
public class ObservabilityAutoConfiguration {
}
