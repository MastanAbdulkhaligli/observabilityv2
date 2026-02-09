package az.magusframework.components.lib.aspectj.support;

import az.magusframework.components.lib.observability.logging.LoggingContext;

public final class MdcScope implements AutoCloseable {

    public MdcScope(String service, String layer, String operation) {
        LoggingContext.putServiceContext(service, layer, operation);
    }

    @Override
    public void close() {
        LoggingContext.clearContextOnly();
    }
}
