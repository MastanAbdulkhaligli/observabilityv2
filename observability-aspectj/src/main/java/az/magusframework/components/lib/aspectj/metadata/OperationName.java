package az.magusframework.components.lib.aspectj.metadata;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;

public final class OperationName {
    private OperationName() {}

    public static String of(JoinPoint jp) {
        if (jp == null) return "Unknown#unknown";

        String cls = resolveClassSimpleName(jp);
        String mtd = resolveMethodName(jp);

        return sanitizeAndClamp(cls + "#" + mtd, 64);
    }

    private static String resolveClassSimpleName(JoinPoint jp) {
        Object target = jp.getTarget();
        if (target != null) return safeToken(target.getClass().getSimpleName(), "Unknown");
        Signature sig = jp.getSignature();
        if (sig != null && sig.getDeclaringType() != null) return safeToken(sig.getDeclaringType().getSimpleName(), "Unknown");
        return "Unknown";
    }

    private static String resolveMethodName(JoinPoint jp) {
        Signature sig = jp.getSignature();
        if (sig instanceof MethodSignature ms && ms.getMethod() != null) return safeToken(ms.getMethod().getName(), "unknown");
        if (sig != null) return safeToken(sig.getName(), "unknown");
        return "unknown";
    }

    private static String sanitizeAndClamp(String v, int maxLen) {
        if (v == null) return "Unknown#unknown";
        String s = v.trim();
        if (s.isEmpty()) return "Unknown#unknown";

        // allow only safe characters in tag values
        s = s.replaceAll("[^A-Za-z0-9_\\-\\.#]", "_");

        return (s.length() > maxLen) ? s.substring(0, maxLen) : s;
    }

    private static String safeToken(String v, String def) {
        if (v == null) return def;
        String s = v.trim();
        if (s.isEmpty()) return def;
        return s.replaceAll("[^A-Za-z0-9_\\-\\.]", "_");
    }

    /** If you ever need DB-operation guessing, use method-only. */
    public static String methodOnly(String operation) {
        if (operation == null) return "";
        int i = operation.indexOf('#');
        return (i >= 0 && i + 1 < operation.length()) ? operation.substring(i + 1) : operation;
    }
}
