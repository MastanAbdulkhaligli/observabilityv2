package az.magusframework.components.lib.aspectj.policy;

import org.aspectj.lang.ProceedingJoinPoint;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public final class AspectjObservationPolicy {

    public enum Layer { CONTROLLER, SERVICE, REPOSITORY }

    public record LayerConfig(
            boolean enabled,
            List<String> includePackages,
            List<String> excludePackages,
            List<Pattern> includeRegex,
            List<Pattern> excludeRegex
    ) {}

    public record Config(
            boolean enabled,
            LayerConfig controller,
            LayerConfig service,
            LayerConfig repository
    ) {}

    private static final AtomicReference<Config> CFG = new AtomicReference<>(defaultConfig());

    private AspectjObservationPolicy() {}

    public static void set(Config cfg) {
        CFG.set(Objects.requireNonNull(cfg));
    }

    public static boolean isEnabled() {
        return CFG.get().enabled();
    }

    public static boolean shouldObserve(Layer layer, ProceedingJoinPoint pjp) {
        Config cfg = CFG.get();
        if (!cfg.enabled()) return false;

        LayerConfig lc = switch (layer) {
            case CONTROLLER -> cfg.controller();
            case SERVICE -> cfg.service();
            case REPOSITORY -> cfg.repository();
        };
        if (lc == null || !lc.enabled()) return false;

        String declaringType = (pjp == null || pjp.getSignature() == null)
                ? ""
                : pjp.getSignature().getDeclaringTypeName();

        // include packages
        if (lc.includePackages() != null && !lc.includePackages().isEmpty()) {
            boolean ok = lc.includePackages().stream().anyMatch(declaringType::startsWith);
            if (!ok) return false;
        }

        // exclude packages
        if (lc.excludePackages() != null && !lc.excludePackages().isEmpty()) {
            boolean blocked = lc.excludePackages().stream().anyMatch(declaringType::startsWith);
            if (blocked) return false;
        }

        // include regex
        if (lc.includeRegex() != null && !lc.includeRegex().isEmpty()) {
            boolean ok = lc.includeRegex().stream().anyMatch(p -> p.matcher(declaringType).find());
            if (!ok) return false;
        }

        // exclude regex
        if (lc.excludeRegex() != null && !lc.excludeRegex().isEmpty()) {
            boolean blocked = lc.excludeRegex().stream().anyMatch(p -> p.matcher(declaringType).find());
            if (blocked) return false;
        }

        return true;
    }

    private static Config defaultConfig() {
        LayerConfig allOn = new LayerConfig(true, List.of(), List.of(), List.of(), List.of());
        return new Config(true, allOn, allOn, allOn);
    }
}