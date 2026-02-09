package az.magusframework.components.lib.aspectj.metadata;

import az.magusframework.components.lib.observability.core.tags.Layer;

public record InvocationMetadata(
        Layer layer,
        String operation
) {}
