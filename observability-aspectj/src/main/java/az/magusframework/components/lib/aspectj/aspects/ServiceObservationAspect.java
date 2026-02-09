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

@Aspect
public final class ServiceObservationAspect {
    @Pointcut("execution(* *(..)) && within(*..service..*)")
    public void anyServiceMethod() {}

    @Around("anyServiceMethod()")
    public Object observeService(ProceedingJoinPoint pjp) throws Throwable {

        ObservabilityBootstrap.ensureInitialized();

        InvocationMetadata meta =
                InvocationMetadataExtractor.forService(pjp);

        MetricTags tags =
                BaseTagsFactory.forInvocation(meta);


        return ObservationExecutor.observe(pjp, meta, tags);
    }
}
