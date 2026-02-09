package az.magusframework.components.lib.aspectj.tags;

import az.magusframework.components.lib.aspectj.bootstrap.ObservabilityBootstrap;
import az.magusframework.components.lib.aspectj.metadata.InvocationMetadata;
import az.magusframework.components.lib.observability.core.tags.MetricTag;
import az.magusframework.components.lib.observability.core.tags.MetricTags;

public final class BaseTagsFactory {


    private BaseTagsFactory() {}

    public static MetricTags forInvocation(InvocationMetadata meta) {
        return MetricTags.empty()
                .with(new MetricTag("app", ObservabilityBootstrap.appName()))
                .with(new MetricTag("service", ObservabilityBootstrap.serviceName()))
                .with(new MetricTag("layer", meta.layer().name()))
                .with(new MetricTag("operation", meta.operation()));
    }
}
