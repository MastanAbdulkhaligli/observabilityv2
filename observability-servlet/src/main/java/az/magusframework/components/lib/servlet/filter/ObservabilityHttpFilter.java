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
import az.magusframework.components.lib.observability.core.tracing.span.*;
import az.magusframework.components.lib.observability.logging.LoggingContext;
import az.magusframework.components.lib.observability.tracing.TraceMdcScope;
import az.magusframework.components.lib.servlet.body.RequestBodyExtractor;

import az.magusframework.components.lib.servlet.route.HttpRouteResolver;
import az.magusframework.components.lib.servlet.route.RouteBucketing;
import az.magusframework.components.lib.servlet.support.CachedBodyHttpServletRequest;
import az.magusframework.components.lib.servlet.support.ResponseHeaderMap;
import az.magusframework.components.lib.servlet.support.StatusCaptureResponseWrapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

public final class ObservabilityHttpFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityHttpFilter.class);

    private static final TraceIdGenerator TRACE_ID_GENERATOR = new RandomTraceIdGenerator();

    private static final String RUNTIME_HOST = resolveHostname();
    private static final String RUNTIME_IP = resolveHostIp();
    private static final String RUNTIME_NODE = resolveNodeName();

    private final MetricsRecorder metricsRecorder;
    private final HttpRouteResolver routeResolver;
    private final RequestBodyExtractor bodyExtractor;

    private final String appName;          // avoid ObservabilityBootstrap dependency
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
        this.serviceName = (serviceName == null || serviceName.isBlank()) ? "demo-service" : serviceName;
        this.serviceModule = (serviceModule == null || serviceModule.isBlank()) ? "unknown-module" : serviceModule;
        this.serviceComponent = (serviceComponent == null || serviceComponent.isBlank()) ? "unknown-component" : serviceComponent;
        this.serviceEnv = (serviceEnv == null || serviceEnv.isBlank()) ? "local" : serviceEnv;

        this.maxCachedBodyBytes = Math.max(0, maxCachedBodyBytes);
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) {

        if (!(req instanceof HttpServletRequest request) || !(res instanceof HttpServletResponse response)) {
            safeDo(chain, req, res);
            return;
        }

        // Skip /error duplication pattern (works for many Spring Boot setups too; harmless elsewhere)
        String uri = request.getRequestURI();
        if ("/error".equals(uri)) {
            safeDo(chain, req, res);
            return;
        }

        final long startNano = System.nanoTime();
        final TimerSample httpSample = metricsRecorder.startTimer("obs.http.server.duration");

        HttpServletRequest workingRequest = request;
        StatusCaptureResponseWrapper workingResponse = new StatusCaptureResponseWrapper(response);

        // Optional request body caching (only if you configured >0)
        if (maxCachedBodyBytes > 0) {
            try {
                workingRequest = new CachedBodyHttpServletRequest(request, maxCachedBodyBytes);
            } catch (Exception e) {
                // If we fail to cache, proceed without it
                workingRequest = request;
            }
        }

        // 1) Correlation
        String correlationId = headerFirstNonBlank(workingRequest, "X-Request-Id", "x-request-id");
        if (correlationId == null) correlationId = UUID.randomUUID().toString();

        LoggingContext.putCorrelationId(correlationId);
        workingResponse.setHeader("X-Request-Id", correlationId);

        // 2) Early HTTP context
        String httpMethodUpper = safeUpper(workingRequest.getMethod());
        String clientIp = workingRequest.getRemoteAddr();

        String route = RouteBucketing.bucketEarly(workingRequest.getRequestURI());
        LoggingContext.putHttpMethodAndPath(workingRequest.getMethod(), workingRequest.getRequestURI());
        LoggingContext.putHttpDirection("INBOUND");
        LoggingContext.putHttpClientIp((clientIp != null && !clientIp.isBlank()) ? clientIp : "unknown");
        LoggingContext.putHttpRoute(route);

        // 3) Service + runtime metadata
        LoggingContext.putServiceMetadata(serviceName, serviceModule, serviceComponent, serviceEnv);
        // you also have module; keep it in your LoggingContext if you support it:
        LoggingContext.putRuntimeContext(RUNTIME_HOST, RUNTIME_IP, RUNTIME_NODE);

        // 4) Trace propagation (inbound)
        TracePropagation.Extracted extracted = TracePropagation.extract(extractHeaders(workingRequest));
        SpanContext inboundParentTc = extracted.inboundParent();
        String baggage = extracted.baggage();
        if (baggage != null && !baggage.isBlank()) {
            MDC.put(LogFields.Correlation.BAGGAGE, baggage);
        }

        // 5) Root span name and initial tags (route is bucketed early)
        String rootSpanName = httpMethodUpper + " " + route;
        if (rootSpanName.length() > 100) rootSpanName = rootSpanName.substring(0, 100);

        MetricTags initialTags = TagsFactory.httpServer(
                appName,
                serviceName,
                workingRequest.getMethod(),
                route,
                "unknown"
        );

        Span httpSpan = null;
        Throwable thrown = null;

        try {
            // Create HTTP root span
            var inboundParent = new SpanContext(
                    inboundParentTc.traceId(),
                    inboundParentTc.spanId(),
                    null, // Parent ID of the incoming request is usually not needed here
                    true  // Sampled flag
            );
            httpSpan = Tracing.get().httpRootSpan(rootSpanName, initialTags, inboundParent, TRACE_ID_GENERATOR);

            // Inject outbound trace headers (response headers)
            TracePropagation.injectOutbound(
                    new ResponseHeaderMap(workingResponse),
                    httpSpan.context(),
                    baggage
            );

            // Run under span + MDC
            try (TraceMdcScope ignoredMdc = TraceMdcScope.set(httpSpan);
                 SpanScope ignoredScope = httpSpan.activate()) {

                chain.doFilter(workingRequest, workingResponse);

            } catch (Throwable t) {
                thrown = t;

                ErrorInfo info = ErrorClassifier.classify(t, null);
                httpSpan.recordError(info.kind(), info.code());
                if (info.kind() == ErrorKind.TECHNICAL) {
                    httpSpan.recordException(t);
                }

                // rethrow to preserve semantics
                throw t;
            }

        } catch (Throwable businessThrowable) {
            // We MUST not swallow; container expects it.
            // But finalize in finally.
            sneakyThrow(businessThrowable);

        } finally {
            try {
                // Status is best-effort captured (wrapper)
                int status = workingResponse.getCapturedStatus();
                if (thrown != null && status < 400) status = 500;

                long durationMs = (System.nanoTime() - startNano) / 1_000_000L;

                // Extract Magus IDs (optional hook)
                try {
                    bodyExtractor.extract(workingRequest);
                } catch (Throwable ignored) {
                    // never break request
                }

                // Resolve final low-cardinality route (Spring module may provide template resolver)
                String finalRoute = null;
                try {
                    finalRoute = routeResolver.resolveRoute(workingRequest, workingResponse);
                } catch (Throwable ignored) {
                    // ignore
                }
                if (finalRoute != null && !finalRoute.isBlank()) {
                    route = RouteBucketing.clamp(finalRoute);
                    LoggingContext.putHttpRoute(route);
                }

                LoggingContext.putHttpStatusAndDuration(status, durationMs);

                Outcome outcomeEnum = ErrorClassifier.determineOutcome(status, thrown);
                String outcomeStr = outcomeToMdc(outcomeEnum);
                String statusClass = ErrorClassifier.resolveStatusClass(status);
                LoggingContext.putHttpStatusAndDuration(status, durationMs);
                LoggingContext.putHttpOutcome(outcomeStr);

                // set error kind/code at end too (status-aware), if relevant
                if (thrown != null || status >= 400) {
                    ErrorInfo info = ErrorClassifier.classify(thrown, status);
                    LoggingContext.putErrorKindAndCode(info.kind().name(), info.code());
                }

                MetricTags finalTags = TagsFactory.httpServer(
                        appName,
                        serviceName,
                        workingRequest.getMethod(),
                        route,
                        statusClass
                ).with(new MetricTag("outcome", outcomeEnum.name()));

                metricsRecorder.stopTimer(httpSample, finalTags, outcomeEnum);

                if (httpSpan != null) {
                    try (TraceMdcScope ignored = TraceMdcScope.set(httpSpan)) {
                        httpSpan.setStatus(outcomeEnum == Outcome.SUCCESS ? SpanStatus.OK : SpanStatus.ERROR);
                        log.info("HTTP request completed");
                        httpSpan.end();
                    }
                } else {
                    log.info("HTTP request completed");
                }

            } catch (Throwable ignored) {
                // Observability must never break request processing
                try {
                    if (httpSpan != null) httpSpan.end();
                } catch (Throwable ignore2) {
                    // ignore
                }
            } finally {
                // Cleanup
                try {
                    LoggingContext.clearAll();
                    MDC.remove(LogFields.Correlation.BAGGAGE);
                    SpanHolder.clear();
                } catch (Throwable ignored) {
                    // ignore
                }
            }
        }
    }

    private static void safeDo(FilterChain chain, ServletRequest req, ServletResponse res) {
        try {
            chain.doFilter(req, res);
        } catch (Exception e) {
            sneakyThrow(e);
        }
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

    private static Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> map = new HashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            map.put(name, request.getHeader(name));
        }
        return map;
    }

    private static String statusClass(int status) {
        if (status >= 200 && status < 300) return "2xx";
        if (status >= 300 && status < 400) return "3xx";
        if (status >= 400 && status < 500) return "4xx";
        if (status >= 500 && status < 600) return "5xx";
        return "unknown";
    }

    private static Outcome mapHttpOutcome(int status, Throwable error) {
        if (error != null) {
            ErrorInfo info = ErrorClassifier.classify(error, status);
            return (info.kind() == ErrorKind.BUSINESS) ? Outcome.BUSINESS_ERROR : Outcome.TECHNICAL_ERROR;
        }
        if (status >= 200 && status < 400) return Outcome.SUCCESS;
        if (status == 408) return Outcome.TIMEOUT;
        if (status >= 400 && status < 500) return Outcome.BUSINESS_ERROR;
        return Outcome.TECHNICAL_ERROR;
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

    private static String resolveNodeName() {
        String node = System.getenv("K8S_NODE_NAME");
        return (node != null && !node.isBlank()) ? node : RUNTIME_HOST;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable t) throws T {
        throw (T) t;
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