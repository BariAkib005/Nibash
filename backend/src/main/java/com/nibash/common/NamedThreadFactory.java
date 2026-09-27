package com.nibash.common;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Names the threads of the application's own pools ({@code mail-1}, {@code device-3}, …) so a
 * thread dump or a log line says which pool did the work. Daemon threads never hold the JVM open
 * on shutdown; each pool is also shut down explicitly by its owner.
 */
public final class NamedThreadFactory implements ThreadFactory {

    private final String prefix;
    private final AtomicInteger counter = new AtomicInteger();

    public NamedThreadFactory(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public Thread newThread(Runnable task) {
        Thread thread = new Thread(task, prefix + "-" + counter.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    }
}
