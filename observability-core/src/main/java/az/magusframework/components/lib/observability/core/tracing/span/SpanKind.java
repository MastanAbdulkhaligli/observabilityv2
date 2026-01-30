package az.magusframework.components.lib.observability.core.tracing.span;

/**
 * <h1>SpanKind</h1>
 * <p>
 * Defines the relationship between the span and its remote or local context.
 * </p>
 * <p>Standardizing these kinds allows tracing backends to identify service boundaries
 * and build accurate service topology maps.</p>
 */
public enum SpanKind {

    /**
     * Indicates that the span covers the server-side handling of an inbound request.
     * <p>Example: An incoming HTTP REST call or a SOAP request.</p>
     */
    SERVER,

    /**
     * Indicates that the span covers the client-side of an outbound request.
     * <p>Example: A call to an external REST API or a database query.</p>
     */
    CLIENT,

    /**
     * Indicates that the span represents internal application logic.
     * <p>Example: A method call within the same service that requires timing.</p>
     */
    INTERNAL,

    /**
     * Indicates that the span represents the production of a message to a queue.
     * <p>Example: Sending a message to RabbitMQ or Kafka.</p>
     */
    PRODUCER,

    /**
     * Indicates that the span represents the consumption of a message from a queue.
     * <p>Example: A listener processing a Kafka record.</p>
     */
    CONSUMER
}