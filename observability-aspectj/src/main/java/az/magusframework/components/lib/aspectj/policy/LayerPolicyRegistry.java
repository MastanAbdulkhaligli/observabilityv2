package az.magusframework.components.lib.aspectj.policy;

import az.magusframework.components.lib.observability.core.tags.Layer;

import java.util.EnumMap;
import java.util.Map;

public final class LayerPolicyRegistry {
    private static final Map<Layer, LayerObservationPolicy> POLICIES =
            new EnumMap<>(Layer.class);

    static {
        // DEFAULT = existing behavior (no change)
        POLICIES.put(Layer.CONTROLLER, LayerObservationPolicy.full());
        POLICIES.put(Layer.SERVICE, LayerObservationPolicy.full());
        POLICIES.put(Layer.REPOSITORY, LayerObservationPolicy.full());
    }

    private LayerPolicyRegistry() {}

    public static LayerObservationPolicy policyFor(Layer layer) {
        return POLICIES.getOrDefault(layer, LayerObservationPolicy.full());
    }

    // ---- future extension hook ----
    public static void override(Layer layer, LayerObservationPolicy policy) {
        POLICIES.put(layer, policy);
    }
}
