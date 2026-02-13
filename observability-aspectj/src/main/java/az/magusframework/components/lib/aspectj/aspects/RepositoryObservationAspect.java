package az.magusframework.components.lib.aspectj.aspects;

import az.magusframework.components.lib.aspectj.bootstrap.ObservabilityBootstrap;
import az.magusframework.components.lib.aspectj.exacution.ObservationExecutor;
import az.magusframework.components.lib.aspectj.metadata.InvocationMetadata;
import az.magusframework.components.lib.aspectj.metadata.InvocationMetadataExtractor;
import az.magusframework.components.lib.aspectj.tags.BaseTagsFactory;
import az.magusframework.components.lib.observability.core.tags.MetricTag;
import az.magusframework.components.lib.observability.core.tags.MetricTags;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

@Aspect
public final class RepositoryObservationAspect {

    private static final Logger log = LoggerFactory.getLogger(RepositoryObservationAspect.class);

    @Pointcut(
            "(" +
                    "execution(* demo..repository..*(..)) || " +
                    "execution(* az.magusframework..repository..*(..))" +
                    ") && " +
                    "!execution(String *.toString()) && " +
                    "!execution(int *.hashCode()) && " +
                    "!execution(boolean *.equals(..)) && " +
                    "!execution(Class *.getClass()) && " +
                    "!execution(void *.wait(..)) && " +
                    "!execution(void *.notify()) && " +
                    "!execution(void *.notifyAll())"
    )
    public void anyRepositoryMethod() { /* marker */ }

    @Around("anyRepositoryMethod()")
    public Object observeRepository(ProceedingJoinPoint pjp) throws Throwable {

        final InvocationMetadata meta;
        final MetricTags tags;

        // Protect ONLY observability setup and tag construction.
        // If repository code throws, it must propagate (no second proceed).
        try {
            ObservabilityBootstrap.ensureInitialized();

            meta = InvocationMetadataExtractor.forRepository(pjp);

            tags = BaseTagsFactory.forInvocation(meta)
                    .with(new MetricTag("entity", resolveEntity(pjp)))
                    .with(new MetricTag("db_operation",
                            normalizeDbOperation(
                                    guessDbOperation(meta.operation())
                            )));
        } catch (Throwable obsFailure) {
            safeLog(pjp, obsFailure, "RepositoryObservationAspect");
            return pjp.proceed(); // fallback ONCE
        }

        // No catch here: prevents double-execution + duplicate logs
        return ObservationExecutor.observe(pjp, meta, tags);
    }

    private static void safeLog(ProceedingJoinPoint pjp, Throwable t, String aspectName) {
        try {
            String sig = (pjp == null || pjp.getSignature() == null)
                    ? "Unknown#unknown"
                    : pjp.getSignature().toShortString();

            log.error("{} failed at {}. type={}, msg={}. Proceeding without observation.",
                    aspectName, sig, t.getClass().getName(), t.getMessage());

            if (log.isDebugEnabled()) {
                log.debug("{} stacktrace", aspectName, t);
            }
        } catch (Throwable ignored) {
            // no-op
        }
    }

    // ---------------- db helpers ----------------

    public static String guessDbOperation(String operation) {
        String method = extractMethodName(operation);
        if (method == null || method.isBlank()) return "UNKNOWN";

        String m = method.toLowerCase(Locale.ROOT);

        if (startsWithAny(m,
                "find", "get", "load", "fetch", "read", "query", "select", "search",
                "list", "stream", "exists", "count")) {
            if (m.startsWith("count")) return "COUNT";
            if (m.startsWith("exists")) return "EXISTS";
            return "SELECT";
        }

        if (startsWithAny(m, "save", "insert", "add", "create", "persist")) return "INSERT";
        if (startsWithAny(m, "update", "patch", "modify", "set")) return "UPDATE";
        if (startsWithAny(m, "delete", "remove", "purge", "truncate")) return "DELETE";
        if (startsWithAny(m, "upsert", "merge")) return "UPSERT";

        return "UNKNOWN";
    }

    private static String extractMethodName(String operation) {
        if (operation == null) return null;
        int i = operation.lastIndexOf('#');
        if (i >= 0 && i + 1 < operation.length()) {
            return operation.substring(i + 1);
        }
        return operation;
    }

    private static boolean startsWithAny(String s, String... prefixes) {
        for (String p : prefixes) {
            if (s.startsWith(p)) return true;
        }
        return false;
    }

    private static String normalizeDbOperation(String op) {
        if (op == null) return "UNKNOWN";
        return switch (op) {
            case "SELECT", "INSERT", "UPDATE", "DELETE", "UPSERT",
                 "CALL", "BATCH", "UNKNOWN", "COUNT", "EXISTS" -> op;
            default -> "UNKNOWN";
        };
    }

    private static String clamp(String v, int max) {
        if (v == null) return "unknown";
        String s = v.trim();
        if (s.isEmpty()) return "unknown";
        return (s.length() > max) ? s.substring(0, max) : s;
    }

    private static String resolveEntity(ProceedingJoinPoint pjp) {
        if (pjp == null || pjp.getTarget() == null) {
            return "unknown";
        }

        String className = pjp.getTarget().getClass().getSimpleName();

        String entity = className.endsWith("Repository")
                ? className.substring(0, className.length() - "Repository".length())
                : className;

        return clamp(entity, 32);
    }
}
