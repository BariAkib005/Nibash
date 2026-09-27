package com.nibash.device;

import com.nibash.common.NamedThreadFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * The TCP server the building's door hardware — intercom panels, card readers, gate controllers —
 * connects to (default port 9500). It runs beside the web API for the life of the application.
 *
 * <p><b>Threads.</b> One dedicated {@code device-acceptor} thread blocks in
 * {@link ServerSocket#accept()} and hands every new socket to a bounded worker pool
 * ({@code device-1…n}, {@code nibash.devices.workers}); each worker then serves one panel for as
 * long as it stays connected ({@link DeviceConnection}). The pool has no queue: when every worker is
 * busy, a new connection is told {@code ERR busy} and closed at once rather than left waiting
 * unanswered. Connected panels are tracked in the thread-safe {@link DeviceRegistry}, which is how
 * a web request on a Tomcat thread reaches a panel to release its door.
 *
 * <p><b>Lifecycle.</b> Starts once the application context is ready and stops before it closes:
 * the server socket is closed (which ends the accept loop), every panel is disconnected, and the
 * pool is shut down.
 */
@Component
public class DeviceGateway implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(DeviceGateway.class);
    private static final int HELLO_TIMEOUT_MS = 10_000;

    private final DeviceService service;
    private final DeviceRegistry registry;
    private final boolean enabled;
    private final int port;
    private final int workers;
    private final int idleTimeoutMs;

    private volatile boolean running;
    private ServerSocket server;
    private ThreadPoolExecutor pool;
    private Thread acceptor;

    public DeviceGateway(DeviceService service, DeviceRegistry registry,
                         @Value("${nibash.devices.enabled:true}") boolean enabled,
                         @Value("${nibash.devices.port:9500}") int port,
                         @Value("${nibash.devices.workers:32}") int workers,
                         @Value("${nibash.devices.idle-timeout-seconds:60}") int idleTimeoutSeconds) {
        this.service = service;
        this.registry = registry;
        this.enabled = enabled;
        this.port = port;
        this.workers = Math.max(1, workers);
        this.idleTimeoutMs = Math.max(1, idleTimeoutSeconds) * 1000;
    }

    @Override
    public synchronized void start() {
        if (!enabled || running) {
            return;
        }
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(port), 50);
        } catch (IOException e) {
            throw new IllegalStateException("The device gateway could not listen on port " + port
                    + ". Set NIBASH_DEVICE_PORT to a free port, or NIBASH_DEVICES_ENABLED=false.", e);
        }
        pool = new ThreadPoolExecutor(workers, workers, 0L, TimeUnit.MILLISECONDS, new SynchronousQueue<>(),
                new NamedThreadFactory("device"), new ThreadPoolExecutor.AbortPolicy());
        running = true;
        acceptor = new Thread(this::acceptLoop, "device-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
        log.info("Device gateway listening on port {} ({} worker threads)", server.getLocalPort(), workers);
    }

    /** Runs on the acceptor thread until the server socket is closed. */
    private void acceptLoop() {
        while (running) {
            Socket socket;
            try {
                socket = server.accept();
            } catch (SocketException e) {
                if (running) {
                    log.warn("Device gateway accept failed: {}", e.getMessage());
                }
                continue; // closed by stop(): the loop condition ends it
            } catch (IOException e) {
                log.warn("Device gateway accept failed: {}", e.getMessage());
                continue;
            }
            try {
                socket.setTcpNoDelay(true);
                socket.setKeepAlive(true);
                pool.execute(new DeviceConnection(socket, service, registry, HELLO_TIMEOUT_MS, idleTimeoutMs));
            } catch (RejectedExecutionException e) {
                refuse(socket, "ERR busy, try again shortly");
            } catch (IOException e) {
                refuse(socket, "ERR connection failed");
            }
        }
    }

    private static void refuse(Socket socket, String message) {
        try (socket; OutputStream out = socket.getOutputStream()) {
            out.write((message + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) {
            // the panel will retry
        }
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        try {
            server.close();
        } catch (IOException ignored) {
            // closing anyway
        }
        registry.closeAll("server shutting down");
        pool.shutdownNow();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Device worker threads did not stop within 5 s");
            }
            acceptor.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("Device gateway stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** The bound port — useful when configured as 0 (any free port), as the tests do. */
    public int port() {
        return server == null ? -1 : server.getLocalPort();
    }

    /** Panels being served right now (each on its own worker thread). */
    public int activeConnections() {
        return pool == null ? 0 : pool.getActiveCount();
    }

    public boolean isEnabled() {
        return enabled;
    }
}
