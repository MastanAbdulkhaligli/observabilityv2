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
 * Fixes based on your logs:
 * 1) startTimer must run AFTER trace/span is placed into MDC (otherwise trace.* is empty in startTimer log).
 * 2) Properly handle "no traceparent" by creating a ROOT span (new traceId/spanId) instead of leaving trace empty.
 * 3) Prevent duplicate /error dispatch from polluting logs by skipping ERROR dispatcher type and "/error" URI.
 * 4) Stop timer + end span under TraceMdcScope so stopTimer also includes trace fields.
 * 5) Support async: if request goes async, finalize (stopTimer/end span/clear MDC) when async completes.
 */
public final class ObservabilityHttpFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityHttpFilter.class);

    private static final TraceIdGenerator TRACE_ID_GENERATOR = new RandomTraceIdGenerator();

    private static final String RUNTIME_HOST = resolveHostname();
    private static final String RUNTIME_IP = resolveHostIp();
    private static final String RUNTIME_NODE = resolveNodeName(RUNTIME_HOST);

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

        // Guard: avoid double-application on re-dispatches (REQUEST -> ERROR/ASYNC)
        if (request.getAttribute(ATTR_GUARD) != null) {
            chain.doFilter(req, res);
            return;
        }

        // Skip Spring Boot error dispatches (/error) to prevent the "ERROR endpoint hit" noise
        if (request.getDispatcherType() == DispatcherType.ERROR) {
            chain.doFilter(req, res);
            return;
        }
        String uri = safeUri(request);
        if ("/error".equals(uri)) {
            chain.doFilter(req, res);
            return;
        }

        request.setAttribute(ATTR_GUARD, Boolean.TRUE);

        HttpServletRequest workingRequest = maybeWrapRequestBody(request);
        StatusCaptureResponseWrapper workingResponse = new StatusCaptureResponseWrapper(response);

        // --------- EARLY CONTEXT (must exist BEFORE startTimer) ----------
        final long startNano = System.nanoTime();

        // Correlation ID
        String correlationId = headerFirstNonBlank(workingRequest, "X-Request-Id", "x-request-id");
        if (correlationId == null) correlationId = UUID.randomUUID().toString();
        LoggingContext.putCorrelationId(correlationId);
        workingResponse.setHeader("X-Request-Id", correlationId);

        // Service + runtime (always)
        LoggingContext.putServiceMetadata(serviceName, serviceModule, serviceComponent, serviceEnv);
        LoggingContext.putRuntimeContext(RUNTIME_HOST, RUNTIME_IP, RUNTIME_NODE);

        // HTTP basic
        final String method = safeUpper(workingRequest.getMethod());
        final String path = safeUri(workingRequest);
        final String clientIp = (workingRequest.getRemoteAddr() == null || workingRequest.getRemoteAddr().isBlank())
                ? "unknown"
                : workingRequest.getRemoteAddr();

        // Early route bucket: low cardinality from raw path (later we may replace with template)
        String route = RouteBucketing.bucketEarly(path);

        LoggingContext.putHttpMethodAndPath(method, path);
        LoggingContext.putHttpDirection("INBOUND");
        LoggingContext.putHttpClientIp(clientIp);
        LoggingContext.putHttpRoute(route);

        // --------- TRACE EXTRACTION + ROOT SPAN CREATION (BEFORE startTimer) ----------
        TracePropagation.Extracted extracted = TracePropagation.extract(extractHeaders(workingRequest));
        SpanContext inboundParent = extracted == null ? null : extracted.inboundParent();
        String baggage = extracted == null ? null : extracted.baggage();
        if (baggage != null && !baggage.isBlank()) {
            MDC.put(LogFields.Correlation.BAGGAGE, baggage);
        }

        // Span name: method + route (bucketed)
        String spanName = method + " " + route;
        if (spanName.length() > 120) spanName = spanName.substring(0, 120);

        // Initial tags (status unknown here)
        MetricTags initialTags = TagsFactory.httpServer(
                appName,
                serviceName,
                method,
                route,
                "unknown"
        );

        // Create span:
        // - if inboundParent valid => child span under inbound
        // - else => create fresh root span (new traceId) by passing null parent (recommended)
        final Span httpSpan = createHttpRootSpan(spanName, initialTags, inboundParent);

        // Put trace context into MDC NOW
        try (TraceMdcScope ignoredMdc = TraceMdcScope.set(httpSpan);
             SpanScope ignoredScope = httpSpan.activate()) {

            // Inject trace context to response headers (so caller can see)
            TracePropagation.injectOutbound(
                    new ResponseHeaderMap(workingResponse),
                    httpSpan.context(),
                    baggage
            );

            // NOW start timer (your NoopMetricsRecorder.startTimer log will include trace.*)
            final TimerSample httpSample = metricsRecorder.startTimer("obs.http.server.duration");

            // Execute chain
            try {
                chain.doFilter(workingRequest, workingResponse);
            } catch (Throwable t) {
                // Record error to span (but don't swallow)
                recordSpanError(httpSpan, t, null);
                throw t;
            } finally {
                // If async started, finalize when async completes
                if (workingRequest.isAsyncStarted()) {
                    attachAsyncFinalizer(workingRequest, workingResponse, httpSpan, httpSample, startNano, route, method, baggage);
                    return; // async finalizer will do cleanup
                }

                // Sync finalization
                finalizeRequest(workingRequest, workingResponse, httpSpan, httpSample, startNano, route, method);
            }

        } finally {
            // In sync case, finalizeRequest clears MDC; in async case, async finalizer will clear.
            // So here we clear only if not async.
            if (!workingRequest.isAsyncStarted()) {
                safeClearAll();
            }
        }
    }

    // =========================================================
    // Finalization logic
    // =========================================================

    private void finalizeRequest(
            HttpServletRequest req,
            StatusCaptureResponseWrapper resp,
            Span span,
            TimerSample sample,
            long startNano,
            String earlyRoute,
            String method
    ) {
        Throwable thrown = null; // in sync path, exception already propagated; status capture covers it
        int status = resp.getCapturedStatus();
        if (status <= 0) status = 200; // wrapper safety

        // Attempt: resolve route template (e.g., "/users/{id}")
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

        // If Spring wrote 200 but error happened, we’d see 5xx in wrapper; keep it as-is.
        LoggingContext.putHttpStatusAndDuration(status, durationMs);

        Outcome outcome = ErrorClassifier.determineOutcome(status, null);
        String outcomeStr = outcomeToMdc(outcome);
        LoggingContext.putHttpOutcome(outcomeStr);

        // Error kind/code for >=400 (status-based)
        if (status >= 400) {
            ErrorInfo info = ErrorClassifier.classify(null, status);
            LoggingContext.putErrorKindAndCode(info.kind().name(), info.code());
        }

        String statusClass = ErrorClassifier.resolveStatusClass(status);

        MetricTags finalTags = TagsFactory.httpServer(
                appName,
                serviceName,
                method,
                route,
                statusClass
        ).with(new MetricTag("outcome", outcome.name()));

        // Stop timer + end span under MDC scope (so stopTimer has trace)
        try (TraceMdcScope ignored = TraceMdcScope.set(span)) {
            span.setStatus(outcome == Outcome.SUCCESS ? SpanStatus.OK : SpanStatus.ERROR);
            metricsRecorder.stopTimer(sample, finalTags, outcome);
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
            String baggage
    ) {
        try {
            req.getAsyncContext().addListener(new AsyncListener() {
                @Override public void onComplete(AsyncEvent event) {
                    try {
                        // Ensure MDC has trace for stopTimer
                        try (TraceMdcScope ignored = TraceMdcScope.set(span)) {
                            finalizeRequest(req, resp, span, sample, startNano, earlyRoute, method);
                        }
                    } finally {
                        safeClearAll();
                    }
                }

                @Override public void onTimeout(AsyncEvent event) {
                    try {
                        recordSpanError(span, null, 504);
                        try (TraceMdcScope ignored = TraceMdcScope.set(span)) {
                            // force status to 504 if not already set
                            resp.setStatus(504);
                            finalizeRequest(req, resp, span, sample, startNano, earlyRoute, method);
                        }
                    } finally {
                        safeClearAll();
                    }
                }

                @Override public void onError(AsyncEvent event) {
                    try {
                        Throwable t = event.getThrowable();
                        recordSpanError(span, t, 500);
                        try (TraceMdcScope ignored = TraceMdcScope.set(span)) {
                            // If container didn't set status, assume 500
                            int st = resp.getCapturedStatus();
                            if (st < 400) resp.setStatus(500);
                            finalizeRequest(req, resp, span, sample, startNano, earlyRoute, method);
                        }
                    } finally {
                        safeClearAll();
                    }
                }

                @Override public void onStartAsync(AsyncEvent event) {
                    // no-op
                }
            });
        } catch (Throwable ignored) {
            // Worst case: fall back to immediate finalization
            finalizeRequest(req, resp, span, sample, startNano, earlyRoute, method);
            safeClearAll();
        }
    }

    // =========================================================
    // Span creation + error recording
    // =========================================================

    private Span createHttpRootSpan(String name, MetricTags tags, SpanContext inboundParent) {
        // If inboundParent is invalid, treat as "no parent" -> new trace
        SpanContext parent = isValidInboundParent(inboundParent) ? inboundParent : null;
        return Tracing.get().httpRootSpan(name, tags, parent, TRACE_ID_GENERATOR);
    }

    private void recordSpanError(Span span, Throwable t, Integer httpStatusOrNull) {
        try {
            ErrorInfo info = ErrorClassifier.classify(t, httpStatusOrNull);
            span.recordError(info.kind(), info.code());

            // Set MDC error fields too (so logs have error.kind/code if you log inside the request)
            LoggingContext.putErrorKindAndCode(info.kind().name(), info.code());

            if (t != null) {
                LoggingContext.putErrorFromThrowableIfAbsent(t, "HTTP_FILTER");
                if (info.kind() == ErrorKind.TECHNICAL) {
                    span.recordException(t);
                }
            }
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
            // Important: clear MDC + holder to avoid leaking trace into next request
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
