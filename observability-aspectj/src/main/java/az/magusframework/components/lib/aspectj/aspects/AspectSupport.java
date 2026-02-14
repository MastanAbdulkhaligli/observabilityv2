package az.magusframework.components.lib.aspectj.aspects;

import org.aspectj.lang.ProceedingJoinPoint;
import org.slf4j.Logger;

final class AspectSupport {
    private AspectSupport() {}

    static void safeLog(Logger log, ProceedingJoinPoint pjp, Throwable t, String aspectName) {
        try {
            String sig = (pjp == null || pjp.getSignature() == null)
                    ? "Unknown#unknown"
                    : pjp.getSignature().toShortString();

            log.error("{} failed at {}. type={}, msg={}. Proceeding without observation.",
                    aspectName, sig, t.getClass().getName(), t.getMessage());

            if (log.isDebugEnabled()) log.debug("{} stacktrace", aspectName, t);
        } catch (Throwable ignored) {
            // no-op
        }
    }

    static boolean isMatchMode(String mode, String expected) {
        if (mode == null) return false;
        return expected.equalsIgnoreCase(mode.trim());
    }
}