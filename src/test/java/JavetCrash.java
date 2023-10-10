import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Host;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.callback.JavetCallbackContext;
import com.caoccao.javet.utils.JavetResourceUtils;
import com.caoccao.javet.values.V8Value;
import com.caoccao.javet.values.reference.V8ValueObject;
import com.caoccao.javet.values.reference.V8ValuePromise;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

// TODO: update dependency to Javet 3.0.0
public class JavetCrash {

    @Test
    public void testJavetCrash() throws JavetException, NoSuchMethodException {
        try (
                V8Runtime runtime = V8Host.getV8Instance().createV8Runtime();
                V8ValueObject context = runtime.createV8ValueObject()
        ) {
            AsyncMethodWrapperV2 wrapper = getAsyncMethodWrapper(runtime);
            JavetCallbackContext receiver = new JavetCallbackContext("invoke", wrapper, wrapper.getInvokeMethod());
            context.bindFunction(receiver);

            runtime.getExecutor("function main(context) { " +
                    "var complexClass = {'hello': 'hello'}; context.invoke(complexClass); }"
            ).executeVoid();

            final int REPEAT_COUNT = 100000;
            for (int i = 0; i < REPEAT_COUNT; i++) {
                while (wrapper.queue.size() > 10) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(10L);
                        runtime.lowMemoryNotification();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                System.out.println("Repeat iteration #" + i);
                runtime.getGlobalObject().invokeVoid("main", context);
            }

            try {
                wrapper.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            context.unbindFunction(receiver.getName());
            runtime.lowMemoryNotification();
            System.out.println("Completed with " + runtime.getReferenceCount());
        }
    }

    @NotNull
    private static AsyncMethodWrapperV2 getAsyncMethodWrapper(V8Runtime runtime) {
        return new AsyncMethodWrapperV2(runtime, parameter -> {
            try {
                String hello = parameter.getString("hello");
                assert hello.equals("hello");
            } catch (JavetException e) {
                throw new RuntimeException(e);
            }
            try {
                return runtime.createV8ValueString("Hello world!");
            } catch (JavetException e) {
                throw new RuntimeException(e);
            }
        });
    }
}

class AsyncMethodWrapperV2 implements AutoCloseable, Runnable {
    protected Thread daemonThread;
    protected ConcurrentLinkedQueue<Task> queue;
    protected volatile boolean quitting;
    protected V8Runtime v8Runtime;
    private final java.util.function.Function<V8ValueObject, V8Value> block;

    public AsyncMethodWrapperV2(
            V8Runtime runtime,
            java.util.function.Function<V8ValueObject, V8Value> block
    ) {
        v8Runtime = runtime;
        this.block = block;

        queue = new ConcurrentLinkedQueue<>();
        quitting = false;
        daemonThread = new Thread(this);
        daemonThread.setName("MockFS Daemon");
        daemonThread.start();
    }

    @Override
    public void close() throws Exception {
        quitting = true;
        daemonThread.join();
    }

    public void invoke(V8ValueObject object) throws JavetException {
        V8ValuePromise v8ValuePromiseResolver = v8Runtime.createV8ValuePromise();
        queue.add(new Task(v8ValuePromiseResolver, object.toClone()));
    }


    public java.lang.reflect.Method getInvokeMethod() throws NoSuchMethodException {
        return this.getClass().getMethod("invoke", V8ValueObject.class);
    }

    @Override
    public void run() {
        while (!quitting || !queue.isEmpty()) {
            final int length = queue.size();
            for (int i = 0; i < length; ++i) {
                Task task = queue.poll();
                if (task == null) {
                    break;
                }
                try (
                        V8ValuePromise promise = task.promise;
                        V8ValueObject object = task.object
                ) {
                    Thread.sleep(0);
                    promise.resolve(block.apply(object));
                } catch (JavetException | InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    JavetResourceUtils.safeClose(task.promise, task.object);

                }
            }
            try {
                TimeUnit.MILLISECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
            }
        }
        System.out.println("CLOSED ALL!");
    }

    static class Task {
        private final V8ValueObject object;
        private final V8ValuePromise promise;

        public Task(V8ValuePromise promise, V8ValueObject object) {
            this.object = object;
            this.promise = promise;
        }
    }
}
