package az.magusframework.components.lib.observability.core.error;


import az.magusframework.components.lib.observability.core.metrics.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Unit Tests for ErrorClassifier")
class ErrorClassifierTest {

    @Nested
    @DisplayName("Tests for classify()")
    class ClassifyTests {

        @Test
        @DisplayName("Should classify 401 and 403 as TECHNICAL (Auth Failure)")
        void shouldClassifyAuthIssuesAsTechnical() {
            ErrorInfo info401 = ErrorClassifier.classify(new RuntimeException(), 401);
            ErrorInfo info403 = ErrorClassifier.classify(null, 403);

            assertEquals(ErrorKind.TECHNICAL, info401.kind());
            assertEquals(ErrorClassifier.CODE_AUTH_FAILURE, info401.code());
            assertEquals(ErrorClassifier.CODE_AUTH_FAILURE, info403.code());
        }

        @Test
        @DisplayName("Should classify 429 as TECHNICAL (Throttled)")
        void shouldClassifyRateLimitingAsTechnical() {
            ErrorInfo info = ErrorClassifier.classify(new Exception("Too many requests"), 429);
            assertEquals(ErrorKind.TECHNICAL, info.kind());
            assertEquals(ErrorClassifier.CODE_THROTTLED, info.code());
        }

        @ParameterizedTest
        @ValueSource(ints = {500, 502, 503, 504})
        @DisplayName("Should classify 5xx status codes as TECHNICAL")
        void shouldClassify5xxAsTechnical(int status) {
            ErrorInfo info = ErrorClassifier.classify(null, status);
            assertEquals(ErrorKind.TECHNICAL, info.kind());
            assertEquals(ErrorClassifier.CODE_SERVER_ERROR, info.code());
        }

        @ParameterizedTest
        @ValueSource(ints = {400, 404, 409})
        @DisplayName("Should classify standard 4xx as BUSINESS")
        void shouldClassifyStandard4xxAsBusiness(int status) {
            ErrorInfo info = ErrorClassifier.classify(null, status);
            assertEquals(ErrorKind.BUSINESS, info.kind());
            assertEquals(ErrorClassifier.CODE_CLIENT_ERROR, info.code());
        }

        @Test
        @DisplayName("Should use UNEXPECTED_RUNTIME_EXCEPTION for unknown scenarios")
        void shouldHandleNullStatusAndError() {
            ErrorInfo info = ErrorClassifier.classify(new RuntimeException(), null);
            assertEquals(ErrorKind.TECHNICAL, info.kind());
            assertEquals(ErrorClassifier.CODE_UNEXPECTED, info.code());
        }

        @Test
        @DisplayName("Should verify that Class Name Caching works (Performance Check)")
        void shouldCacheExceptionClassName() {
            RuntimeException ex = new RuntimeException("test");

            // First call (populates cache)
            ErrorInfo first = ErrorClassifier.classify(ex, 500);
            // Second call (hits cache)
            ErrorInfo second = ErrorClassifier.classify(ex, 500);

            assertEquals("RuntimeException", first.exceptionName());
            assertEquals(first.exceptionName(), second.exceptionName());
        }
    }

    @Nested
    @DisplayName("Tests for determineOutcome()")
    class OutcomeTests {

        @Test
        @DisplayName("Should return SUCCESS for 2xx and 3xx statuses with no error")
        void shouldReturnSuccessForHealthyCalls() {
            assertEquals(Outcome.SUCCESS, ErrorClassifier.determineOutcome(200, null));
            assertEquals(Outcome.SUCCESS, ErrorClassifier.determineOutcome(302, null));
        }

        @Test
        @DisplayName("Should return TECHNICAL_ERROR if exception is present")
        void shouldReturnTechnicalErrorIfExceptionExists() {
            assertEquals(Outcome.TECHNICAL_ERROR, ErrorClassifier.determineOutcome(200, new Exception()));
        }

        @Test
        @DisplayName("Should return BUSINESS_ERROR for 4xx statuses")
        void shouldReturnBusinessErrorFor4xx() {
            assertEquals(Outcome.BUSINESS_ERROR, ErrorClassifier.determineOutcome(400, null));
            assertEquals(Outcome.BUSINESS_ERROR, ErrorClassifier.determineOutcome(404, null));
        }
    }

    @ParameterizedTest(name = "Status {0} should be class {1}")
    @CsvSource({
            "200, 2xx",
            "201, 2xx",
            "404, 4xx",
            "500, 5xx",
            "302, 3xx",
            ", unknown"
    })
    @DisplayName("Should resolve correct status class for dashboards")
    void shouldResolveStatusClass(Integer status, String expectedClass) {
        assertEquals(expectedClass, ErrorClassifier.resolveStatusClass(status));
    }
}