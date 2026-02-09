package az.magusframework.components.lib.aspectj.policy;

public final class LayerObservationPolicy {
    private final boolean createSpan;
    private final boolean recordMetrics;
    private final boolean classifyErrors;

    private LayerObservationPolicy(
            boolean createSpan,
            boolean recordMetrics,
            boolean classifyErrors
    ) {
        this.createSpan = createSpan;
        this.recordMetrics = recordMetrics;
        this.classifyErrors = classifyErrors;
    }


    public boolean createSpan() {
        return createSpan;
    }

    public boolean recordMetrics() {
        return recordMetrics;
    }

    public boolean classifyErrors() {
        return classifyErrors;
    }


    // ---- factory methods ----

    public static LayerObservationPolicy full() {
        return new LayerObservationPolicy(true, true, true);
    }

    public static LayerObservationPolicy noSpan() {
        return new LayerObservationPolicy(false, true, true);
    }

    public static LayerObservationPolicy metricsOnly() {
        return new LayerObservationPolicy(false, true, false);
    }

    public static LayerObservationPolicy disabled() {
        return new LayerObservationPolicy(false, false, false);
    }

}
