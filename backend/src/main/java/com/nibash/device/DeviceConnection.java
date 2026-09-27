package com.nibash.device;

import com.nibash.common.Times;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One connected panel, served by one thread of the gateway's worker pool for as long as the TCP
 * connection lasts.
 *
 * <p>The protocol is plain text, one command per line (UTF-8, {@code \n}), so a panel's firmware —
 * or {@code nc}/telnet — can speak it:
 *
 * <pre>
 *   panel → server                     server → panel
 *   HELLO &lt;device_id&gt; [secret]         OK &lt;device name&gt;  |  ERR &lt;reason&gt; (and the line closes)
 *   PING                               PONG
 *   RING [flat]                        OK
 *   CARD &lt;card number&gt;                 ALLOW &lt;first name&gt;  |  DENY &lt;reason&gt;
 *   DOOR OPENED | DOOR CLOSED          OK
 *   EVENT &lt;type&gt; [details]             OK
 *   BYE                                BYE (and the line closes)
 *                                      OPEN &lt;seconds&gt; &lt;name&gt;   ← pushed when someone releases the
 *                                                                 door from the app
 * </pre>
 *
 * <p><b>Two threads write to one socket:</b> this connection's own thread answers the panel, and a web
 * request thread pushes {@code OPEN} when a guard presses the button. {@link #send} is
 * {@code synchronized} so two lines never interleave. Reads happen on this thread only, with a
 * timeout: a panel that says nothing for the idle period (it should {@code PING} well inside it)
 * is treated as gone and its line closed.
 */
final class DeviceConnection implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(DeviceConnection.class);
    static final int MAX_LINE = 256;
    private static final Pattern EVENT_TYPE = Pattern.compile("[a-z][a-z0-9_]{0,49}");

    private final Socket socket;
    private final DeviceService service;
    private final DeviceRegistry registry;
    private final int helloTimeoutMs;
    private final int idleTimeoutMs;
    private final BufferedReader in;
    private final Writer out;
    private final LocalDateTime connectedAt = Times.now();

    private volatile DeviceService.Session session;
    private volatile LocalDateTime lastSeen = connectedAt;
    private volatile String closeReason = "connection closed by the panel";

    DeviceConnection(Socket socket, DeviceService service, DeviceRegistry registry, int helloTimeoutMs,
                     int idleTimeoutMs) throws IOException {
        this.socket = socket;
        this.service = service;
        this.registry = registry;
        this.helloTimeoutMs = helloTimeoutMs;
        this.idleTimeoutMs = idleTimeoutMs;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public void run() {
        String address = socket.getInetAddress().getHostAddress();
        try {
            socket.setSoTimeout(helloTimeoutMs);
            session = handshake();
            if (session == null) {
                return;
            }
            DeviceConnection previous = registry.register(session.deviceId(), this);
            if (previous != null) {
                previous.close("replaced by a new connection from the same panel");
            }
            send("OK " + session.deviceName());
            service.log(session.deviceId(), "online", "Connected from " + address + ".");
            log.info("Device {} ({}) online from {}", session.deviceId(), session.deviceName(), address);

            socket.setSoTimeout(idleTimeoutMs);
            String line;
            while ((line = readLine()) != null) {
                lastSeen = Times.now();
                if (!line.isBlank() && !handle(line.trim())) {
                    closeReason = "the panel said BYE";
                    break;
                }
            }
        } catch (SocketTimeoutException e) {
            closeReason = session == null ? "no HELLO in time" : "silent for " + idleTimeoutMs / 1000 + " s";
            trySend("ERR timeout");
        } catch (LineTooLong e) {
            closeReason = "sent a line over " + MAX_LINE + " characters";
            trySend("ERR line too long");
        } catch (IOException e) {
            // the socket was closed — by the panel, by close() below, or by the network
        } catch (RuntimeException e) {
            closeReason = "server error";
            log.warn("Device connection from {} failed", address, e);
        } finally {
            // Closed here rather than by try-with-resources, which would close it before the catch
            // blocks above could tell the panel why.
            close(closeReason);
            if (session != null && registry.remove(session.deviceId(), this)) {
                safeLog("offline", "Disconnected: " + closeReason + ".");
                log.info("Device {} offline: {}", session.deviceId(), closeReason);
            }
        }
    }

    /** Reads {@code HELLO <id> [secret]} and asks the service to accept it; null if refused. */
    private DeviceService.Session handshake() throws IOException {
        String line = readLine();
        if (line == null) {
            return null;
        }
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 2 || parts.length > 3 || !"HELLO".equalsIgnoreCase(parts[0])) {
            send("ERR say HELLO <device_id> [secret] first");
            return null;
        }
        try {
            return service.authenticate(Long.parseLong(parts[1]), parts.length == 3 ? parts[2] : null,
                    socket.getInetAddress());
        } catch (NumberFormatException e) {
            send("ERR device_id must be a number");
        } catch (DeviceService.Refused e) {
            send("ERR " + e.getMessage());
            log.info("Refused device {} from {}: {}", parts[1], socket.getInetAddress().getHostAddress(), e.getMessage());
        }
        return null;
    }

    /** @return false when the panel is done (BYE) */
    private boolean handle(String line) {
        String[] parts = line.split("\\s+", 2);
        String command = parts[0].toUpperCase(Locale.ROOT);
        String argument = parts.length > 1 ? parts[1].trim() : "";
        try {
            switch (command) {
                case "PING" -> send("PONG");
                case "RING" -> {
                    service.log(session.deviceId(), "ring", argument.isEmpty()
                            ? "Visitor rang at " + session.deviceName() + "."
                            : "Visitor rang flat " + argument + " from " + session.deviceName() + ".");
                    send("OK");
                }
                case "CARD" -> send(argument.isEmpty() ? "ERR usage: CARD <card number>"
                        : service.checkCard(session, argument));
                case "DOOR" -> {
                    String state = argument.toUpperCase(Locale.ROOT);
                    if (!state.equals("OPENED") && !state.equals("CLOSED")) {
                        send("ERR usage: DOOR OPENED | DOOR CLOSED");
                    } else {
                        service.door(session, state.equals("OPENED"));
                        send("OK");
                    }
                }
                case "EVENT" -> {
                    String[] event = argument.split("\\s+", 2);
                    String type = event[0].toLowerCase(Locale.ROOT);
                    if (!EVENT_TYPE.matcher(type).matches()) {
                        send("ERR usage: EVENT <type> [details]");
                    } else {
                        service.log(session.deviceId(), type, event.length > 1 ? event[1] : null);
                        send("OK");
                    }
                }
                case "BYE" -> {
                    send("BYE");
                    return false;
                }
                case "HELLO" -> send("ERR already signed in");
                default -> send("ERR unknown command");
            }
        } catch (RuntimeException e) {
            log.warn("Device {}: '{}' failed", session.deviceId(), command, e);
            send("ERR server error");
        }
        return true;
    }

    /**
     * Writes one line to the panel. Called from this connection's thread and from web request
     * threads, hence {@code synchronized}.
     *
     * @return false if the line could not be written (the connection is then closed)
     */
    synchronized boolean send(String line) {
        try {
            out.write(line);
            out.write('\n');
            out.flush();
            return true;
        } catch (IOException e) {
            close("could not write to the panel");
            return false;
        }
    }

    private void trySend(String line) {
        if (!socket.isClosed()) {
            send(line);
        }
    }

    /** Closes the socket from any thread; the connection's own thread then winds down and logs it. */
    void close(String reason) {
        closeReason = reason;
        try {
            socket.close();
        } catch (IOException ignored) {
            // already closed
        }
    }

    /** A line, without its terminator; null at end of stream. Refuses runaway lines. */
    private String readLine() throws IOException {
        StringBuilder line = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                int end = line.length();
                return end > 0 && line.charAt(end - 1) == '\r' ? line.substring(0, end - 1) : line.toString();
            }
            if (line.length() >= MAX_LINE) {
                throw new LineTooLong();
            }
            line.append((char) c);
        }
        return line.isEmpty() ? null : line.toString();
    }

    private void safeLog(String type, String details) {
        try {
            service.log(session.deviceId(), type, details);
        } catch (RuntimeException e) {
            log.warn("Could not log '{}' for device {}", type, session.deviceId(), e);
        }
    }

    DeviceService.Session session() {
        return session;
    }

    LocalDateTime connectedAt() {
        return connectedAt;
    }

    LocalDateTime lastSeen() {
        return lastSeen;
    }

    String address() {
        return socket.getInetAddress().getHostAddress();
    }

    private static final class LineTooLong extends IOException {
    }
}
