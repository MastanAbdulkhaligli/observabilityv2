package az.magusframework.components.lib.aspectj.metadata;

import az.magusframework.components.lib.observability.core.tags.Layer;
import org.aspectj.lang.JoinPoint;

public final class InvocationMetadataExtractor {
    private InvocationMetadataExtractor() {
    }

    public static InvocationMetadata forService(JoinPoint jp) {
        return new InvocationMetadata(Layer.SERVICE, OperationName.of(jp));
    }

    public static InvocationMetadata forController(JoinPoint jp) {
        return new InvocationMetadata(Layer.CONTROLLER, OperationName.of(jp));
    }

    public static InvocationMetadata forRepository(JoinPoint jp) {
        return new InvocationMetadata(Layer.REPOSITORY, OperationName.of(jp));
    }
}
