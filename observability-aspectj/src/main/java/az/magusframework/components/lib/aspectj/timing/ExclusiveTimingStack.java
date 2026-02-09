package az.magusframework.components.lib.aspectj.timing;

import java.util.ArrayDeque;
import java.util.Deque;

public final class ExclusiveTimingStack {
    private ExclusiveTimingStack() {}

    private static final ThreadLocal<Deque<Frame>> STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    public static Scope enter() {
        Deque<Frame> s = STACK.get();
        Frame f = new Frame(System.nanoTime());
        s.push(f);
        return new Scope(s, f);
    }

    public static final class Result {
        public final long inclusiveNanos;
        public final long exclusiveNanos;

        public Result(long inclusiveNanos, long exclusiveNanos) {
            this.inclusiveNanos = inclusiveNanos;
            this.exclusiveNanos = exclusiveNanos;
        }
    }

    public static final class Scope implements AutoCloseable {
        private final Deque<Frame> stack;
        private final Frame frame;
        private boolean closed;
        private Result result;

        private Scope(Deque<Frame> stack, Frame frame) {
            this.stack = stack;
            this.frame = frame;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;

            long end = System.nanoTime();
            long inclusive = end - frame.startNanos;
            long exclusive = inclusive - frame.childNanos;

            stack.pop();

            // add inclusive time to parent as "child time"
            Frame parent = stack.peek();
            if (parent != null) {
                parent.childNanos += inclusive;
            }

            // cleanup ThreadLocal for top-level exit
            if (stack.isEmpty()) {
                STACK.remove();
            }

            result = new Result(inclusive, exclusive);
        }

        public Result result() {
            if (!closed) throw new IllegalStateException("Scope not closed yet");
            return result;
        }
    }

    private static final class Frame {
        final long startNanos;
        long childNanos;

        Frame(long startNanos) {
            this.startNanos = startNanos;
        }
    }
}
