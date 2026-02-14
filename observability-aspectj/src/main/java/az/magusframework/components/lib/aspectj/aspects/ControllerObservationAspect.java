package az.magusframework.components.lib.aspectj.aspects;

import az.magusframework.components.lib.aspectj.bootstrap.ObservabilityBootstrap;
import az.magusframework.components.lib.aspectj.exacution.ObservationExecutor;
import az.magusframework.components.lib.aspectj.metadata.InvocationMetadata;
import az.magusframework.components.lib.aspectj.metadata.InvocationMetadataExtractor;
import az.magusframework.components.lib.aspectj.policy.AspectjObservationPolicy;
import az.magusframework.components.lib.aspectj.tags.BaseTagsFactory;
import az.magusframework.components.lib.observability.core.tags.MetricTags;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Aspect
public final class ControllerObservationAspect {

    private static final Logger log = LoggerFactory.getLogger(ControllerObservationAspect.class);

    @Pointcut("execution(public * *(..)) && within(*..controller..*)")
    public void anyControllerMethod() { /* marker */ }

    @Around("anyControllerMethod()")
    public Object observeController(ProceedingJoinPoint pjp) throws Throwable {

        // YAML-driven runtime gate
        if (!AspectjObservationPolicy.shouldObserve(AspectjObservationPolicy.Layer.CONTROLLER, pjp)) {
            return pjp.proceed();
        }

        final InvocationMetadata meta;
        final MetricTags tags;

        // Only protect OBSERVABILITY SETUP (no business code has run yet)
        try {
            ObservabilityBootstrap.ensureInitialized();
            meta = InvocationMetadataExtractor.forController(pjp);
            tags = BaseTagsFactory.forInvocation(meta);
        } catch (Throwable obsFailure) {
            safeLog(pjp, obsFailure, "ControllerObservationAspect");
            return pjp.proceed(); // fallback ONCE
        }

        // IMPORTANT: do NOT catch Throwable here, otherwise you may proceed TWICE
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
