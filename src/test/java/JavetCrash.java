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

public class JavetCrash {

    private static ConcurrentLinkedQueue<V8ValuePromise> queue = new ConcurrentLinkedQueue<>();

    @Test
    public void testJavetCrash() throws JavetException, NoSuchMethodException {
        try (
                V8Runtime runtime = V8Host.getV8Instance().createV8Runtime();
                V8ValueObject context = runtime.createV8ValueObject()
        ) {
            JavetCallbackContext receiver = getJavetCallbackContext(runtime);
            context.bindFunction(receiver);

            runtime.getExecutor("function main(context) { " +
                    "var complexClass = {'hello': 'hello'}; return context.invoke(complexClass); }"
            ).executeVoid();

            final int REPEAT_COUNT = 10000;
            for (int i = 0; i < REPEAT_COUNT; i++) {
                System.out.println("Repeat iteration #" + i);
                try (V8Value ignored = runtime.getGlobalObject().invoke("main", context)) {
                    Thread.sleep(0);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            try {
                while (queue.size() < REPEAT_COUNT) {
                    Thread.sleep(10);
                }
                while (!queue.isEmpty()) {
                    V8ValuePromise promise = queue.poll();
                    while (!promise.isFulfilled()) {
                        Thread.sleep(10);
                    }
                    promise.close();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            System.out.println("AAA");
        }


    }

    @NotNull
    private static JavetCallbackContext getJavetCallbackContext(V8Runtime runtime) throws NoSuchMethodException {
        AsyncMethodWrapper callbackObject = new AsyncMethodWrapper(runtime, queue, parameter -> {
            System.out.println("Executing async function");
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

        return new JavetCallbackContext("invoke", callbackObject, callbackObject.getInvokeMethod());
    }
}

class AsyncMethodWrapper {
    private final V8Runtime runtime;
    private final java.util.function.Function<V8ValueObject, V8Value> block;
    private final ConcurrentLinkedQueue<V8ValuePromise> promisesQueue;

    public AsyncMethodWrapper(
            V8Runtime runtime,
            ConcurrentLinkedQueue<V8ValuePromise> promiseQueue,
            java.util.function.Function<V8ValueObject, V8Value> block
    ) {
        this.runtime = runtime;
        this.block = block;
        this.promisesQueue = promiseQueue;
    }

    public V8Value invoke(V8ValueObject parameter) {
        try {
            V8ValuePromise promise = runtime.createV8ValuePromise();
            V8ValuePromise jsPromise = promise.getPromise();
            V8ValueObject clonedParam = parameter.toClone();

            Thread thread = new Thread(() -> {
                try {
                    Thread.sleep(40);
                    promise.resolve(block.apply(clonedParam));
                } catch (JavetException | InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    JavetResourceUtils.safeClose(clonedParam);
                    promisesQueue.add(promise);
                }
            });
            thread.start();

            return jsPromise;
        } catch (JavetException e) {
            throw new RuntimeException(e);
        }
    }


    public java.lang.reflect.Method getInvokeMethod() throws NoSuchMethodException {
        return this.getClass().getMethod("invoke", V8ValueObject.class);
    }
}
