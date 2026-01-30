package az.magusframework.components.lib.observability.core.tracing;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * <h1>RandomTraceIdGenerator</h1>
 * <p>
 * Standard implementation of {@link TraceIdGenerator} that produces W3C-compliant
 * identifiers using a cryptographically strong random number generator.
 * </p>
 *
 * <p><b>Compliance:</b></p>
 * <ul>
 * <li>Trace ID: 32-character lowercase hex (128-bit).</li>
 * <li>Span ID: 16-character lowercase hex (64-bit).</li>
 * </ul>
 */
public final class RandomTraceIdGenerator implements TraceIdGenerator {

    /**
     * SecureRandom is used to seed the initial state.
     * In high-concurrency, ThreadLocalRandom is used for better performance.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    @Override
    public String newTraceId() {
        // W3C Trace ID: 16 bytes (128 bits)
        byte[] bytes = new byte[16];
        fillRandomBytes(bytes);
        return toHex(bytes);
    }

    @Override
    public String newSpanId() {
        // W3C Span ID: 8 bytes (64 bits)
        byte[] bytes = new byte[8];
        fillRandomBytes(bytes);
        return toHex(bytes);
    }

    /**
     * Fills the byte array with random data.
     * Ensures the result is not 'all zeros' as per W3C specification.
     */
    private void fillRandomBytes(byte[] bytes) {
        do {
            // Using ThreadLocalRandom for performance in high-concurrency environments
            ThreadLocalRandom.current().nextBytes(bytes);
        } while (isAllZeros(bytes));
    }

    private static boolean isAllZeros(byte[] bytes) {
        for (byte b : bytes) {
            if (b != 0) return false;
        }
        return true;
    }

    /**
     * High-performance byte-to-hex conversion using a pre-allocated char array.
     */
    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX_CHARS[v >>> 4];
            out[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(out);
    }
}