package com.packhub.util;

import net.minecraft.client.Minecraft;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 后台任务 + 回到客户端线程更新界面。 */
public final class Async {
    private static final ExecutorService POOL = Executors.newCachedThreadPool(new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "PackHub-Async-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    });

    private Async() {
    }

    public static <T> void run(Supplier<T> task, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        POOL.execute(() -> {
            T value = null;
            Throwable error = null;
            try {
                value = task.get();
            } catch (Throwable t) {
                error = t;
            }
            T result = value;
            Throwable failure = error;
            onClient(() -> {
                if (failure != null) {
                    if (onError != null) {
                        onError.accept(failure);
                    }
                } else if (onSuccess != null) {
                    onSuccess.accept(result);
                }
            });
        });
    }

    public static void onClient(Runnable action) {
        try {
            Minecraft.getInstance().execute(action);
        } catch (Throwable t) {
            try {
                action.run();
            } catch (Throwable inner) {
                Log.warn("[PackHub] 客户端回调失败: %s", inner.toString());
            }
        }
    }
}
