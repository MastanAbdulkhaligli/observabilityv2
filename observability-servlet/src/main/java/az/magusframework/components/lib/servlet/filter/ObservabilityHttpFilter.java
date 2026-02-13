package az.magusframework.components.lib.servlet.filter;

import az.magusframework.components.lib.observability.core.error.ErrorClassifier;
import az.magusframework.components.lib.observability.core.error.ErrorInfo;
import az.magusframework.components.lib.observability.core.error.ErrorKind;
import az.magusframework.components.lib.observability.core.logging.LogFields;
import az.magusframework.components.lib.observability.core.metrics.MetricsRecorder;
import az.magusframework.components.lib.observability.core.metrics.Outcome;
import az.magusframework.components.lib.observability.core.metrics.TimerSample;
import az.magusframework.components.lib.observability.core.tags.MetricTag;
import az.magusframework.components.lib.observability.core.tags.MetricTags;
import az.magusframework.components.lib.observability.core.tags.TagsFactory;
import az.magusframework.components.lib.observability.core.tracing.RandomTraceIdGenerator;
import az.magusframework.components.lib.observability.core.tracing.TraceIdGenerator;
import az.magusframework.components.lib.observability.core.tracing.TracePropagation;
import az.magusframework.components.lib.observability.core.tracing.Tracing;
import az.magusframework.components.lib.observability.core.tracing.span.Span;
import az.magusframework.components.lib.observability.core.tracing.span.SpanContext;
import az.magusframework.components.lib.observability.core.tracing.span.SpanHolder;
import az.magusframework.components.lib.observability.core.tracing.span.SpanScope;
import az.magusframework.components.lib.observability.core.tracing.span.SpanStatus;
import az.magusframework.components.lib.observability.logging.LoggingContext;
import az.magusframework.components.lib.observability.tracing.TraceMdcScope;
import az.magusframework.components.lib.servlet.body.RequestBodyExtractor;
import az.magusframework.components.lib.servlet.route.HttpRouteResolver;
import az.magusframework.components.lib.servlet.route.RouteBucketing;
import az.magusframework.components.lib.servlet.support.CachedBodyHttpServletRequest;
import az.magusframework.components.lib.servlet.support.ResponseHeaderMap;
import az.magusframework.components.lib.servlet.support.StatusCaptureResponseWrapper;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * ObservabilityHttpFilter
 *
 * Fixes for your "ERROR scenario":
 * 1) DO NOT skip "/error" URI for initial REQUEST (because you have a real "/error" endpoint for testing).
 * 2) Skip only ERROR dispatcher re-dispatch (DispatcherType.ERROR) to avoid duplicate/empty "error page" noise.
 * 3) When controller throws, capture Throwable -> force status=500 for metrics/outcome if wrapper doesn't.
 * 4) Ensure error.kind/code and outcome are set, and stopTimer/endSpan always happens under TraceMdcScope.
 * 5) Async-safe finalization via AsyncListener (same as before).
 */
public final class ObservabilityHttpFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityHttpFilter.class);

    private static final TraceIdGenerator TRACE_ID_GENERATOR = new RandomTraceIdGenerator();

    private static final String RUNTIME_HOST = resolveHostname();
    private static final String RUNTIME_IP = resolveHostIp();
    private static final String RUNTIME_NODE = resolveNodeName(RUNTIME_HOST);

    /**
     * Guard to avoid double instrumentation on re-dispatches (REQUEST -> ERROR/ASYNC).
     * We still allow the first REQUEST to "/error".
     */
    private static final String ATTR_GUARD = ObservabilityHttpFilter.class.getName() + ".APPLIED";

    private final MetricsRecorder metricsRecorder;
    private final HttpRouteResolver routeResolver;
    private final RequestBodyExtractor bodyExtractor;

    private final String appName;
    private final String serviceName;
    private final String serviceModule;
    private final String serviceComponent;
    private final String serviceEnv;

    private final int maxCachedBodyBytes;

    public ObservabilityHttpFilter(
            MetricsRecorder metricsRecorder,
            HttpRouteResolver routeResolver,
            RequestBodyExtractor bodyExtractor,
            String appName,
            String serviceName,
            String serviceModule,
            String serviceComponent,
            String serviceEnv,
            int maxCachedBodyBytes
    ) {
        this.metricsRecorder = Objects.requireNonNull(metricsRecorder, "metricsRecorder");
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver");
        this.bodyExtractor = Objects.requireNonNull(bodyExtractor, "bodyExtractor");

        this.appName = (appName == null || appName.isBlank()) ? "unknown-app" : appName;
        this.serviceName = (serviceName == null || serviceName.isBlank()) ? "unknown-service" : serviceName;
        this.serviceModule = (serviceModule == null || serviceModule.isBlank()) ? "unknown-module" : serviceModule;
        this.serviceComponent = (serviceComponent == null || serviceComponent.isBlank()) ? "unknown-component" : serviceComponent;
        this.serviceEnv = (serviceEnv == null || serviceEnv.isBlank()) ? "local" : serviceEnv;

        this.maxCachedBodyBytes = Math.max(0, maxCachedBodyBytes);
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws ServletException, IOException {

        if (!(req instanceof HttpServletRequest request) || !(res instanceof HttpServletResponse response)) {
            chain.doFilter(req, res);
            return;
        }

        // Skip only ERROR dispatcher re-dispatch to prevent duplicate/no-context logs from /error page handling.
        // IMPORTANT: this does NOT skip the initial REQUEST to "/error" (your test endpoint).
        if (request.getDispatcherType() == DispatcherType.ERROR) {
            chain.doFilter(req, res);
            return;
        }

        // Guard: if already applied for this request (e.g., ASYNC re-dispatch), don't instrument twice
        if (request.getAttribute(ATTR_GUARD) != null) {
            chain.doFilter(req, res);
            return;
        }
        request.setAttribute(ATTR_GUARD, Boolean.TRUE);

        HttpServletRequest workingRequest = maybeWrapRequestBody(request);
        StatusCaptureResponseWrapper workingResponse = new StatusCaptureResponseWrapper(response);

        final long startNano = System.nanoTime();

        // -------------------- EARLY CONTEXT (BEFORE startTimer) --------------------

        // Correlation ID
        String correlationId = headerFirstNonBlank(workingRequest, "X-Request-Id", "x-request-id");
        if (correlationId == null) correlationId = UUID.randomUUID().toString();
        LoggingContext.putCorrelationId(correlationId);
        workingResponse.setHeader("X-Request-Id", correlationId);

        // Service + runtime
        LoggingContext.putServiceMetadata(serviceName, serviceModule, serviceComponent, serviceEnv);
        LoggingContext.putRuntimeContext(RUNTIME_HOST, RUNTIME_IP, RUNTIME_NODE);

        // HTTP basics
        final String method = safeUpper(workingRequest.getMethod());
        final String path = safeUri(workingRequest);
        final String clientIp = (workingRequest.getRemoteAddr() == null || workingRequest.getRemoteAddr().isBlank())
                ? "unknown"
                : workingRequest.getRemoteAddr();

        String route = RouteBucketing.bucketEarly(path);

        LoggingContext.putHttpMethodAndPath(method, path);
        LoggingContext.putHttpDirection("INBOUND");
        LoggingContext.putHttpClientIp(clientIp);
        LoggingContext.putHttpRoute(route);

        // -------------------- TRACE EXTRACTION + SPAN CREATION --------------------

        TracePropagation.Extracted extracted = TracePropagation.extract(extractHeaders(workingRequest));
        SpanContext inboundParent = extracted == null ? null : extracted.inboundParent();
        String baggage = extracted == null ? null : extracted.baggage();
        if (baggage != null && !baggage.isBlank()) {
            MDC.put(LogFields.Correlation.BAGGAGE, baggage);
        }

        String spanName = method + " " + route;
        if (spanName.length() > 120) spanName = spanName.substring(0, 120);

        MetricTags initialTags = TagsFactory.httpServer(
                appName,
                serviceName,
                method,
                route,
                "unknown"
        );

        final Span httpSpan = createHttpSpan(spanName, initialTags, inboundParent);

        // Activate span + put trace into MDC BEFORE startTimer
        TimerSample httpSample = null;
        Throwable thrown = null;

        try (TraceMdcScope ignoredMdc = TraceMdcScope.set(httpSpan);
             SpanScope ignoredScope = httpSpan.activate()) {

            // Make trace visible to caller
            TracePropagation.injectOutbound(
                    new ResponseHeaderMap(workingResponse),
                    httpSpan.context(),
                    baggage
            );

            // NOW start timer (startTimer logs will include trace.*)
            httpSample = metricsRecorder.startTimer("obs.http.server.duration");

            try {
                chain.doFilter(workingRequest, workingResponse);
            } catch (Throwable t) {
                thrown = t;

                // record span error immediately (so even if status is missing, span is red)
                recordSpanError(httpSpan, t, null);

                // rethrow so app semantics remain unchanged
                throw t;
            } finally {
                // Async: finalize on completion
                if (workingRequest.isAsyncStarted()) {
                    attachAsyncFinalizer(workingRequest, workingResponse, httpSpan, httpSample, startNano, route, method, thrown);
                    return;
                }

                // Sync finalize
                finalizeRequest(workingRequest, workingResponse, httpSpan, httpSample, startNano, route, method, thrown);
            }

        } finally {
            // In sync path, finalizeRequest clears MDC. In async path, AsyncListener clears MDC.
            if (!workingRequest.isAsyncStarted()) {
                safeClearAll();
            }
        }
    }

    // =========================================================
    // Finalization
    // =========================================================

    private void finalizeRequest(
            HttpServletRequest req,
            StatusCaptureResponseWrapper resp,
            Span span,
            TimerSample sample,
            long startNano,
            String earlyRoute,
            String method,
            Throwable thrown
    ) {
        // Determine status
        int status = resp.getCapturedStatus();
        if (status <= 0) status = 200;

        // If we threw and container didn't set status properly yet, force 500 for metrics correctness
        if (thrown != null && status < 400) {
            status = 500;
            try {
                resp.setStatus(500);
            } catch (Throwable ignored) {
            }
        }

        // Resolve final route template (optional)
        String route = earlyRoute;
        try {
            String resolved = routeResolver.resolveRoute(req, resp);
            if (resolved != null && !resolved.isBlank()) {
                route = RouteBucketing.clamp(resolved);
                LoggingContext.putHttpRoute(route);
            }
        } catch (Throwable ignored) {
        }

        // Duration
        long durationMs = (System.nanoTime() - startNano) / 1_000_000L;
        LoggingContext.putHttpStatusAndDuration(status, durationMs);

        // Outcome should consider throwable too
        Outcome outcome = ErrorClassifier.determineOutcome(status, thrown);
        LoggingContext.putHttpOutcome(outcomeToMdc(outcome));

        // Error kind/code (status-aware) if error happened or >=400
        if (thrown != null || status >= 400) {
            ErrorInfo info = ErrorClassifier.classify(thrown, status);
            LoggingContext.putErrorKindAndCode(info.kind().name(), info.code());

            // Ensure span carries error consistently
            try {
                span.recordError(info.kind(), info.code());
                if (thrown != null && info.kind() == ErrorKind.TECHNICAL) {
                    span.recordException(thrown);
                }
            } catch (Throwable ignored) {
            }
        }

        String statusClass = ErrorClassifier.resolveStatusClass(status);

        MetricTags finalTags = TagsFactory.httpServer(
                appName,
                serviceName,
                method,
                route,
                statusClass
        ).with(new MetricTag("outcome", outcome.name()));

        // Stop timer + end span under TraceMdcScope so stopTimer contains trace
        try (TraceMdcScope ignored = TraceMdcScope.set(span)) {
            span.setStatus(outcome == Outcome.SUCCESS ? SpanStatus.OK : SpanStatus.ERROR);

            if (sample != null) {
                metricsRecorder.stopTimer(sample, finalTags, outcome);
            }

            log.info("HTTP request completed");
        } catch (Throwable ignored) {
        } finally {
            try {
                span.end();
            } catch (Throwable ignored) {
            }
        }
    }

    private void attachAsyncFinalizer(
            HttpServletRequest req,
            StatusCaptureResponseWrapper resp,
            Span span,
            TimerSample sample,
            long startNano,
            String earlyRoute,
            String method,
            Throwable thrown
    ) {
        try {
            req.getAsyncContext().addListener(new AsyncListener() {
                @Override
                public void onComplete(AsyncEvent event) {
                    try {
                        try (TraceMdcScope ignored = TraceMdcScope.set(span)) {
                            finalizeRequest(req, resp, span, sample, startNano, earlyRoute, method, thrown);
                        }
                    } finally {
                        safeClearAll();
                    }
                }

                @Override
                public void onTimeout(AsyncEvent event) {
                    try {
                        try (TraceMdcScope ignored = TraceMdcScope.set(span)) {
                            try {
                                resp.setStatus(504);
                            } catch (Throwable ignored2) {
                            }
                            recordSpanError(span, event.getThrowable(), 504);
                            finalizeRequest(req, resp, span, sample, startNano, earlyRoute, method, event.getThrowable());
                        }
                    } finally {
                        safeClearAll();
                    }
                }

                @Override
                public void onError(AsyncEvent event) {
                    try {
                        Throwable t = event.getThrowable();
                        try (TraceMdcScope ignored = TraceMdcScope.set(span)) {
                            recordSpanError(span, t, 500);
                            int st = resp.getCapturedStatus();
                            if (st < 400) {
                                try {
                                    resp.setStatus(500);
                                } catch (Throwable ignored2) {
                                }
                            }
                            finalizeRequest(req, resp, span, sample, startNano, earlyRoute, method, t);
                        }
                    } finally {
                        safeClearAll();
                    }
                }

                @Override
                public void onStartAsync(AsyncEvent event) {
                    // no-op
                }
            });
        } catch (Throwable ignored) {
            // Worst case: finalize immediately
            finalizeRequest(req, resp, span, sample, startNano, earlyRoute, method, thrown);
            safeClearAll();
        }
    }

    // =========================================================
    // Span creation + error recording
    // =========================================================

    private Span createHttpSpan(String name, MetricTags tags, SpanContext inboundParent) {
        SpanContext parent = isValidInboundParent(inboundParent) ? inboundParent : null;
        // If parent is null -> Tracing creates a new trace (root span)
        return Tracing.get().httpRootSpan(name, tags, parent, TRACE_ID_GENERATOR);
    }

    private void recordSpanError(Span span, Throwable t, Integer httpStatusOrNull) {
        try {
            ErrorInfo info = ErrorClassifier.classify(t, httpStatusOrNull);
            span.recordError(info.kind(), info.code());
            if (t != null && info.kind() == ErrorKind.TECHNICAL) {
                span.recordException(t);
            }

            // Put error in MDC (so your app logs inside request show it)
            LoggingContext.putErrorKindAndCode(info.kind().name(), info.code());
        } catch (Throwable ignored) {
        }
    }

    // =========================================================
    // Helpers
    // =========================================================

    private HttpServletRequest maybeWrapRequestBody(HttpServletRequest request) {
        if (maxCachedBodyBytes <= 0) return request;
        try {
            return new CachedBodyHttpServletRequest(request, maxCachedBodyBytes);
        } catch (Throwable ignored) {
            return request;
        }
    }

    private static boolean isValidInboundParent(SpanContext tc) {
        if (tc == null) return false;
        String tid = tc.traceId();
        String sid = tc.spanId();
        return tid != null && !tid.isBlank() && sid != null && !sid.isBlank();
    }

    private static String headerFirstNonBlank(HttpServletRequest request, String... names) {
        for (String n : names) {
            String v = request.getHeader(n);
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static String safeUpper(String m) {
        if (m == null) return "UNKNOWN";
        return m.toUpperCase(Locale.ROOT);
    }

    private static String safeUri(HttpServletRequest request) {
        String u = request.getRequestURI();
        return (u == null || u.isBlank()) ? "/" : u;
    }

    private static Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> map = new HashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            map.put(name, request.getHeader(name));
        }
        return map;
    }

    private static String resolveHostname() {
        String envHost = System.getenv("HOSTNAME");
        if (envHost != null && !envHost.isBlank()) return envHost;
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown-host";
        }
    }

    private static String resolveHostIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "unknown-ip";
        }
    }

    private static String resolveNodeName(String fallbackHost) {
        String node = System.getenv("K8S_NODE_NAME");
        return (node != null && !node.isBlank()) ? node : fallbackHost;
    }

    private static void safeClearAll() {
        try {
            LoggingContext.clearAll();
            MDC.remove(LogFields.Correlation.BAGGAGE);
            SpanHolder.clear();
        } catch (Throwable ignored) {
        }
    }

    private static String outcomeToMdc(Outcome outcome) {
        if (outcome == null) return "UNKNOWN";
        return switch (outcome) {
            case SUCCESS -> "SUCCESS";
            case CANCELED -> "CANCELED";
            case BUSINESS_ERROR -> "BUSINESS_ERROR";
            case TECHNICAL_ERROR -> "TECHNICAL_ERROR";
            case TIMEOUT -> "TIMEOUT";
        };
    }
}
