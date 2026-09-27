import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A stand-in for a building's door panel (intercom + card reader + door sensor), for trying the
 * Nibash device gateway without hardware. Plain JDK, one file — run it straight from source:
 *
 * <pre>
 *   java tools/device-simulator/DeviceSimulator.java --device 1
 *   java tools/device-simulator/DeviceSimulator.java --device 1 --host 192.168.1.10 --port 9500 --secret s3cret
 *   java tools/device-simulator/DeviceSimulator.java --device 1 --demo     # ring, swipe two cards, then wait
 * </pre>
 *
 * Then type commands — {@code ring 01A}, {@code card GLH-AC-0001}, {@code open}, {@code close},
 * {@code quit} — or press <i>Open door</i> on the Safety &amp; access page and watch the door
 * release here.
 *
 * <p>Like real firmware it does several things at once, on separate threads:
 * <ul>
 *   <li><b>main</b> — reads your commands from the keyboard and sends them;</li>
 *   <li><b>listener</b> — reads everything the server sends: replies, and {@code OPEN} commands
 *       pushed at any moment when someone releases the door from the app;</li>
 *   <li><b>timers</b> — a heartbeat {@code PING} every 20 s, so the server knows the panel is
 *       alive, and closing the door again a few seconds after it was released.</li>
 * </ul>
 * All of them write to the one socket, so {@link #send} is {@code synchronized}.
 */
public class DeviceSimulator {

    private final Socket socket;
    private final BufferedReader in;
    private final Writer out;
    private final ScheduledExecutorService timers = Executors.newScheduledThreadPool(2, task -> {
        Thread thread = new Thread(task, "timers");
        thread.setDaemon(true);
        return thread;
    });
    private final CountDownLatch disconnected = new CountDownLatch(1);

    private DeviceSimulator(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        String host = option(args, "--host", "localhost");
        int port = Integer.parseInt(option(args, "--port", "9500"));
        String device = option(args, "--device", null);
        String secret = option(args, "--secret", "");
        if (device == null) {
            System.out.println("Usage: java DeviceSimulator.java --device <id> [--host localhost] [--port 9500] "
                    + "[--secret s] [--demo]");
            System.out.println("The device id is on the Safety & access page (or GET /api/intercom/devices/).");
            System.exit(2);
        }

        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        DeviceSimulator panel = new DeviceSimulator(socket);

        // The handshake happens before the listener starts, so its reply is read here.
        panel.send(("HELLO " + device + " " + secret).trim());
        String welcome = panel.in.readLine();
        System.out.println("server> " + welcome);
        if (welcome == null || !welcome.startsWith("OK")) {
            System.exit(1);
        }

        Thread listener = new Thread(panel::listen, "listener");
        listener.setDaemon(true);
        listener.start();
        panel.timers.scheduleAtFixedRate(() -> panel.send("PING"), 20, 20, TimeUnit.SECONDS);

        if (hasFlag(args, "--demo")) {
            panel.demo();
        }
        panel.readKeyboard();
    }

    /** The listener thread: prints every line from the server and acts on pushed commands. */
    private void listen() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("OPEN")) {
                    released(line);
                } else if (!line.equals("PONG")) {
                    System.out.println("server> " + line);
                }
            }
            System.out.println("-- the server closed the connection");
        } catch (IOException e) {
            System.out.println("-- connection lost: " + e.getMessage());
        } finally {
            disconnected.countDown();
            System.exit(0);
        }
    }

    /** {@code OPEN <seconds> <name>}: release the door, report it, and close it again later. */
    private void released(String command) {
        String[] parts = command.split("\\s+", 3);
        int seconds = parts.length > 1 ? Integer.parseInt(parts[1]) : 5;
        String by = parts.length > 2 ? " by " + parts[2] : "";
        System.out.println("** door released for " + seconds + " s" + by);
        send("DOOR OPENED");
        timers.schedule(() -> {
            System.out.println("** door closed");
            send("DOOR CLOSED");
        }, seconds, TimeUnit.SECONDS);
    }

    /** The main thread: keyboard commands until {@code quit} or end of input. */
    private void readKeyboard() throws IOException, InterruptedException {
        System.out.println("Commands: ring [flat] | card <number> | open | close | event <type> [details] | quit");
        BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String input;
        while ((input = keyboard.readLine()) != null) {
            String[] parts = input.trim().split("\\s+", 2);
            String argument = parts.length > 1 ? " " + parts[1] : "";
            switch (parts[0].toLowerCase(Locale.ROOT)) {
                case "" -> { }
                case "ring" -> send("RING" + argument);
                case "card" -> send("CARD" + argument);
                case "open" -> send("DOOR OPENED");
                case "close" -> send("DOOR CLOSED");
                case "event" -> send("EVENT" + argument);
                case "quit", "exit" -> {
                    send("BYE");
                    disconnected.await(3, TimeUnit.SECONDS);
                    return;
                }
                default -> System.out.println("?  ring [flat] | card <number> | open | close | event <type> | quit");
            }
        }
        // stdin closed (e.g. --demo run in the background): stay online until the server hangs up
        disconnected.await();
    }

    private void demo() throws InterruptedException {
        String[][] steps = {{"RING 01A", "a visitor rings flat 01A"}, {"CARD GLH-AC-0001", "Ayesha swipes her card"},
                {"DOOR OPENED", "the door opens"}, {"DOOR CLOSED", "...and closes"}, {"CARD LOST-9999", "an unknown card"}};
        for (String[] step : steps) {
            System.out.println("demo: " + step[1]);
            send(step[0]);
            Thread.sleep(1200);
        }
        System.out.println("demo: done - press Open door on the Safety & access page to release the door");
    }

    /** Called from the main, listener and timer threads; one line at a time. */
    private synchronized void send(String line) {
        try {
            out.write(line + "\n");
            out.flush();
        } catch (IOException e) {
            System.out.println("-- could not send '" + line + "': " + e.getMessage());
        }
    }

    private static String option(String[] args, String name, String fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(name)) {
                return args[i + 1];
            }
        }
        return fallback;
    }

    private static boolean hasFlag(String[] args, String name) {
        for (String arg : args) {
            if (arg.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
