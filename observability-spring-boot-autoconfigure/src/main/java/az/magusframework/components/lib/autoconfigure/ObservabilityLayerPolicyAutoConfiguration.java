package az.magusframework.components.lib.autoconfigure;


import az.magusframework.components.lib.aspectj.policy.LayerObservationPolicy;
import az.magusframework.components.lib.aspectj.policy.LayerPolicyRegistry;
import az.magusframework.components.lib.observability.core.tags.Layer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;

@AutoConfiguration
@EnableConfigurationProperties(ObservabilityProperties.class)
@ConditionalOnClass({LayerPolicyRegistry.class, LayerObservationPolicy.class})
public class ObservabilityLayerPolicyAutoConfiguration {
    private final ObservabilityProperties props;

    public ObservabilityLayerPolicyAutoConfiguration(ObservabilityProperties props) {
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void applyPolicies() {
        var layerProps = props.getLayer();
        if (layerProps == null) return;

        override(Layer.CONTROLLER, layerProps.getController());
        override(Layer.SERVICE, layerProps.getService());
        override(Layer.REPOSITORY, layerProps.getRepository());
    }

    private static void override(Layer layer, String policyName) {
        LayerPolicyRegistry.override(layer, parse(policyName));
    }

    private static LayerObservationPolicy parse(String name) {
        String v = (name == null) ? "full" : name.trim().toLowerCase();
        return switch (v) {
            case "full" -> LayerObservationPolicy.full();
            case "nospan" -> LayerObservationPolicy.noSpan();
            case "metricsonly" -> LayerObservationPolicy.metricsOnly();
            case "disabled" -> LayerObservationPolicy.disabled();
            default -> LayerObservationPolicy.full();
        };
    }
}
