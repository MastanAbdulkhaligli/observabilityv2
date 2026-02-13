package az.magusframework.components.lib.aspectj.aspects;

import az.magusframework.components.lib.aspectj.bootstrap.ObservabilityBootstrap;
import az.magusframework.components.lib.aspectj.exacution.ObservationExecutor;
import az.magusframework.components.lib.aspectj.metadata.InvocationMetadata;
import az.magusframework.components.lib.aspectj.metadata.InvocationMetadataExtractor;
import az.magusframework.components.lib.aspectj.tags.BaseTagsFactory;
import az.magusframework.components.lib.observability.core.tags.MetricTags;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Aspect
public final class ServiceObservationAspect {

    private static final Logger log = LoggerFactory.getLogger(ServiceObservationAspect.class);

    @Pointcut(
            "(" +
                    "execution(* demo..service..*(..)) || " +
                    "execution(* az.magusframework..service..*(..))" +
                    ") && " +
                    "!execution(String *.toString()) && " +
                    "!execution(int *.hashCode()) && " +
                    "!execution(boolean *.equals(..)) && " +
                    "!execution(Class *.getClass()) && " +
                    "!execution(void *.wait(..)) && " +
                    "!execution(void *.notify()) && " +
                    "!execution(void *.notifyAll())"
    )
    public void anyServiceMethod() { /* marker */ }

    @Around("anyServiceMethod()")
    public Object observeService(ProceedingJoinPoint pjp) throws Throwable {

        final InvocationMetadata meta;
        final MetricTags tags;

        // Protect ONLY observability setup.
        // If business code throws, it must propagate normally (no double proceed).
        try {
            ObservabilityBootstrap.ensureInitialized();
            meta = InvocationMetadataExtractor.forService(pjp);
            tags = BaseTagsFactory.forInvocation(meta);
        } catch (Throwable obsFailure) {
            safeLog(pjp, obsFailure, "ServiceObservationAspect");
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
}
