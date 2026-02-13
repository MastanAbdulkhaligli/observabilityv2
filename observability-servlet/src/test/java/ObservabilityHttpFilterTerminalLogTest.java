//
//import az.magusframework.components.lib.observability.core.metrics.MetricsRecorder;
//import az.magusframework.components.lib.observability.core.metrics.Outcome;
//import az.magusframework.components.lib.observability.core.metrics.TimerSample;
//import az.magusframework.components.lib.observability.core.tags.MetricTags;
//import az.magusframework.components.lib.observability.core.tags.TagsFactory;
//import az.magusframework.components.lib.observability.core.tracing.TracePropagation;
//import az.magusframework.components.lib.observability.core.tracing.Tracing;
//import az.magusframework.components.lib.observability.core.tracing.span.*;
//import az.magusframework.components.lib.observability.tracing.TraceMdcScope;
//import az.magusframework.components.lib.servlet.body.RequestBodyExtractor;
//import az.magusframework.components.lib.servlet.filter.ObservabilityHttpFilter;
//import az.magusframework.components.lib.servlet.route.HttpRouteResolver;
//import ch.qos.logback.classic.Logger;
//import ch.qos.logback.classic.spi.ILoggingEvent;
//import ch.qos.logback.core.read.ListAppender;
//import jakarta.servlet.FilterChain;
//import jakarta.servlet.ServletRequest;
//import jakarta.servlet.ServletResponse;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import org.junit.jupiter.api.*;
//import org.mockito.MockedStatic;
//import org.slf4j.LoggerFactory;
//
//import java.time.OffsetDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.*;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
//class ObservabilityHttpFilterTerminalLogTest {
//
//    private MetricsRecorder metricsRecorder;
//    private TimerSample timerSample;
//
//    private HttpRouteResolver routeResolver;
//    private RequestBodyExtractor bodyExtractor;
//
//    private HttpServletRequest request;
//    private HttpServletResponse response;
//    private FilterChain chain;
//
//    private ObservabilityHttpFilter filter;
//    private ListAppender<ILoggingEvent> listAppender;
//
//    @BeforeEach
//    void setup() {
//        metricsRecorder = mock(MetricsRecorder.class);
//        timerSample = mock(TimerSample.class);
//        when(metricsRecorder.startTimer(anyString())).thenReturn(timerSample);
//
//        routeResolver = mock(HttpRouteResolver.class);
//        bodyExtractor = mock(RequestBodyExtractor.class);
//
//        request = mock(HttpServletRequest.class);
//        response = mock(HttpServletResponse.class);
//        chain = mock(FilterChain.class);
//
//        when(request.getMethod()).thenReturn("POST");
//        when(request.getRequestURI()).thenReturn("/test");
//        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
//        when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());
//        when(routeResolver.resolveRoute(any(), any())).thenReturn("/test");
//
//        filter = new ObservabilityHttpFilter(
//                metricsRecorder,
//                routeResolver,
//                bodyExtractor,
//                "demo-app",
//                "demo-service",
//                "module",
//                "component",
//                "local",
//                0
//        );
//
//        Logger logger = (Logger) LoggerFactory.getLogger(ObservabilityHttpFilter.class);
//        listAppender = new ListAppender<>();
//        listAppender.start();
//        logger.addAppender(listAppender);
//    }
//
//    @AfterEach
//    void tearDown() {
//        Logger logger = (Logger) LoggerFactory.getLogger(ObservabilityHttpFilter.class);
//        if (listAppender != null) {
//            logger.detachAppender(listAppender);
//            listAppender.stop();
//        }
//    }
//
//    @Test
//    void prints_terminal_logs_like_production_success() throws Exception {
//
//        doAnswer(inv -> {
//            HttpServletResponse resp = (HttpServletResponse) inv.getArguments()[1];
//            resp.setStatus(200);
//            return null;
//        }).when(chain).doFilter(any(ServletRequest.class), any(ServletResponse.class));
//
//        Span span = mock(Span.class);
//        SpanScope spanScope = mock(SpanScope.class);
//        when(span.activate()).thenReturn(spanScope);
//
//        // Valid context (injectOutbound uses it)
//        SpanContext spanCtx = new SpanContext(
//                "67e5f8558770125e2e6796d4808709ed",
//                "fca5fbaaedb39fd2",
//                null,
//                true
//        );
//        when(span.context()).thenReturn(spanCtx);
//
//        // inbound parent must be valid because filter constructs new SpanContext from it
//        az.magusframework.components.lib.observability.core.tracing.span.SpanContext inboundParentTc =
//                mock(az.magusframework.components.lib.observability.core.tracing.span.SpanContext.class);
//        when(inboundParentTc.traceId()).thenReturn("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
//        when(inboundParentTc.spanId()).thenReturn("bbbbbbbbbbbbbbbb");
//
//        try (MockedStatic<Tracing> tracingStatic = mockStatic(Tracing.class);
//             MockedStatic<TracePropagation> propagationStatic = mockStatic(TracePropagation.class);
//             MockedStatic<TagsFactory> tagsFactoryStatic = mockStatic(TagsFactory.class);
//             MockedStatic<TraceMdcScope> mdcStatic = mockStatic(TraceMdcScope.class)) {
//
//            // ✅ Correct: Tracing.get() returns SpanFactory
//            SpanFactory factory = mock(SpanFactory.class);
//            tracingStatic.when(Tracing::get).thenReturn(factory);
//
//            when(factory.httpRootSpan(anyString(), any(MetricTags.class), any(SpanContext.class), any()))
//                    .thenReturn(span);
//
//            MetricTags initialTags = mock(MetricTags.class);
//            MetricTags finalTags = mock(MetricTags.class);
//            when(finalTags.with(any())).thenReturn(finalTags);
//
//            tagsFactoryStatic.when(() ->
//                    TagsFactory.httpServer(anyString(), anyString(), anyString(), anyString(), anyString())
//            ).thenReturn(initialTags, finalTags);
//
//            TracePropagation.Extracted extracted = mock(TracePropagation.Extracted.class);
//            when(extracted.inboundParent()).thenReturn(inboundParentTc);
//            when(extracted.baggage()).thenReturn(null);
//            propagationStatic.when(() -> TracePropagation.extract(anyMap())).thenReturn(extracted);
//
//            propagationStatic.when(() ->
//                    TracePropagation.injectOutbound(anyMap(), any(SpanContext.class), any())
//            ).thenAnswer(i -> null);
//
//            mdcStatic.when(() -> TraceMdcScope.set(any(Span.class))).thenReturn(mock(TraceMdcScope.class));
//
//            filter.doFilter(request, response, chain);
//        }
//
//        // Print to terminal
//        listAppender.list.forEach(e -> System.out.println(toJsonLine(e)));
//
//        verify(metricsRecorder).stopTimer(eq(timerSample), any(MetricTags.class), eq(Outcome.SUCCESS));
//        verify(span).end();
//        verify(response).setHeader(eq("X-Request-Id"), anyString());
//    }
//
//    @Test
//    void prints_terminal_logs_like_production_exception_path() throws Exception {
//
//        doThrow(new RuntimeException("database failure"))
//                .when(chain).doFilter(any(ServletRequest.class), any(ServletResponse.class));
//
//        Span span = mock(Span.class);
//        SpanScope spanScope = mock(SpanScope.class);
//        when(span.activate()).thenReturn(spanScope);
//
//        // valid context
//        when(span.context()).thenReturn(new SpanContext(
//                "11111111111111111111111111111111",
//                "2222222222222222",
//                null,
//                true
//        ));
//
//        az.magusframework.components.lib.observability.core.tracing.span.SpanContext inboundParentTc =
//                mock(az.magusframework.components.lib.observability.core.tracing.span.SpanContext.class);
//        when(inboundParentTc.traceId()).thenReturn("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
//        when(inboundParentTc.spanId()).thenReturn("bbbbbbbbbbbbbbbb");
//
//        try (MockedStatic<Tracing> tracingStatic = mockStatic(Tracing.class);
//             MockedStatic<TracePropagation> propagationStatic = mockStatic(TracePropagation.class);
//             MockedStatic<TagsFactory> tagsFactoryStatic = mockStatic(TagsFactory.class);
//             MockedStatic<TraceMdcScope> mdcStatic = mockStatic(TraceMdcScope.class)) {
//
//            SpanFactory factory = mock(SpanFactory.class);
//            tracingStatic.when(Tracing::get).thenReturn(factory);
//
//            when(factory.httpRootSpan(anyString(), any(MetricTags.class), any(SpanContext.class), any()))
//                    .thenReturn(span);
//
//            MetricTags initialTags = mock(MetricTags.class);
//            MetricTags finalTags = mock(MetricTags.class);
//            when(finalTags.with(any())).thenReturn(finalTags);
//
//            tagsFactoryStatic.when(() ->
//                    TagsFactory.httpServer(anyString(), anyString(), anyString(), anyString(), anyString())
//            ).thenReturn(initialTags, finalTags);
//
//            TracePropagation.Extracted extracted = mock(TracePropagation.Extracted.class);
//            when(extracted.inboundParent()).thenReturn(inboundParentTc);
//            when(extracted.baggage()).thenReturn(null);
//            propagationStatic.when(() -> TracePropagation.extract(anyMap())).thenReturn(extracted);
//
//            propagationStatic.when(() ->
//                    TracePropagation.injectOutbound(anyMap(), any(SpanContext.class), any())
//            ).thenAnswer(i -> null);
//
//            mdcStatic.when(() -> TraceMdcScope.set(any(Span.class))).thenReturn(mock(TraceMdcScope.class));
//
//            assertThrows(RuntimeException.class, () -> filter.doFilter(request, response, chain));
//        }
//
//        listAppender.list.forEach(e -> System.out.println(toJsonLine(e)));
//
//        verify(metricsRecorder).stopTimer(eq(timerSample), any(MetricTags.class), eq(Outcome.TECHNICAL_ERROR));
//        verify(span).recordError(any(), any());
//        verify(span).end();
//    }
//
//    // ---------- JSON-ish printer ----------
//
//    private static String toJsonLine(ILoggingEvent e) {
//        Map<String, Object> root = new LinkedHashMap<>();
//        root.put("timestamp", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
//        root.put("level", e.getLevel().levelStr);
//        root.put("logger", e.getLoggerName());
//        root.put("thread", e.getThreadName());
//        root.put("message", e.getFormattedMessage());
//
//        Map<String, String> mdc = e.getMDCPropertyMap();
//        root.putAll(nestByDots(mdc));
//        return compactJson(root);
//    }
//
//    @SuppressWarnings("unchecked")
//    private static Map<String, Object> nestByDots(Map<String, String> flat) {
//        Map<String, Object> root = new LinkedHashMap<>();
//        if (flat == null) return root;
//
//        for (var entry : flat.entrySet()) {
//            String key = entry.getKey();
//            String val = entry.getValue();
//            if (key == null) continue;
//
//            String[] parts = key.split("\\.");
//            Map<String, Object> cur = root;
//
//            for (int i = 0; i < parts.length; i++) {
//                String p = parts[i];
//                boolean last = (i == parts.length - 1);
//
//                if (last) cur.put(p, val);
//                else cur = (Map<String, Object>) cur.computeIfAbsent(p, k -> new LinkedHashMap<>());
//            }
//        }
//        return root;
//    }
//
//    private static String compactJson(Object obj) {
//        if (obj == null) return "null";
//        if (obj instanceof String s) return "\"" + escape(s) + "\"";
//        if (obj instanceof Map<?, ?> map) {
//            StringBuilder sb = new StringBuilder("{");
//            boolean first = true;
//            for (var e : map.entrySet()) {
//                if (!first) sb.append(",");
//                first = false;
//                sb.append(compactJson(String.valueOf(e.getKey()))).append(":").append(compactJson(e.getValue()));
//            }
//            return sb.append("}").toString();
//        }
//        return "\"" + escape(String.valueOf(obj)) + "\"";
//    }
//
//    private static String escape(String s) {
//        return s.replace("\\", "\\\\").replace("\"", "\\\"");
//    }
//}
