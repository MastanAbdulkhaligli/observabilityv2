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

/**
 * Observes Spring MVC controllers without hardcoding app packages.
 * Matches types annotated with @RestController or @Controller.
 */
@Aspect
public final class ControllerObservationAspect {

    private static final Logger log = LoggerFactory.getLogger(ControllerObservationAspect.class);

    // ---- Pointcuts (annotation-based, no package coupling) ----

    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *) || within(@org.springframework.stereotype.Controller *)")
    public void anyControllerType() { /* marker */ }

    // Only public methods (avoid a lot of framework/internal noise)
    @Pointcut("execution(public * *(..))")
    public void anyPublicMethod() { /* marker */ }

    // Exclude Object methods explicitly
    @Pointcut(
            "!execution(String *.toString()) && " +
                    "!execution(int *.hashCode()) && " +
                    "!execution(boolean *.equals(..)) && " +
                    "!execution(Class *.getClass()) && " +
                    "!execution(void *.wait(..)) && " +
                    "!execution(void *.notify()) && " +
                    "!execution(void *.notifyAll())"
    )
    public void excludeObjectMethods() { /* marker */ }

    @Pointcut("anyControllerType() && anyPublicMethod() && excludeObjectMethods()")
    public void anyControllerMethod() { /* marker */ }

    // ---- Advice ----

    @Around("anyControllerMethod()")
    public Object observeController(ProceedingJoinPoint pjp) throws Throwable {

        final InvocationMetadata meta;
        final MetricTags tags;

        // Protect ONLY observability setup and tag construction.
        // If anything fails here, proceed ONCE without observation.
        try {
            ObservabilityBootstrap.ensureInitialized();
            meta = InvocationMetadataExtractor.forController(pjp);
            tags = BaseTagsFactory.forInvocation(meta);
        } catch (Throwable obsFailure) {
            safeLog(pjp, obsFailure, "ControllerObservationAspect");
            return pjp.proceed();
        }

        // IMPORTANT: do NOT catch Throwable here; avoids double proceed.
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
