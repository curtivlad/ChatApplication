package org.chatapp.registry;

import org.chatapp.entities.ChatRoom;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 * Server centralizat pentru înregistrarea și descoperirea camerelor de chat.
 * ÎMBUNĂTĂȚIT: Acceptă conexiuni din întreaga rețea LAN.
 */
public class RoomRegistryServer extends Thread {
    private static final Logger LOGGER = Logger.getLogger(RoomRegistryServer.class.getName());
    private static final int DEFAULT_PORT = 7777;
    private static final long ROOM_TIMEOUT_MS = 30000; // 30 secunde
    private static final String BIND_ADDRESS = "0.0.0.0"; // Ascultă pe toate interfețele

    private final int port;
    private ServerSocket serverSocket;
    private final Map<String, RegisteredRoom> registeredRooms;
    private final ScheduledExecutorService cleanupScheduler;
    private volatile boolean running;
    private String serverIP; // IP-ul real al serverului

    public RoomRegistryServer(int port) {
        this.port = port;
        this.registeredRooms = new ConcurrentHashMap<>();
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
        this.running = true;

        setupLogging();
        detectServerIP();
    }

    public RoomRegistryServer() {
        this(DEFAULT_PORT);
    }

    private void setupLogging() {
        try {
            ConsoleHandler handler = new ConsoleHandler();
            handler.setLevel(Level.ALL);
            LOGGER.addHandler(handler);
            LOGGER.setLevel(Level.INFO);
        } catch (Exception e) {
            System.err.println("Failed to setup logging: " + e.getMessage());
        }
    }

    /**
     * Detectează IP-ul real al serverului în rețeaua LAN.
     */
    private void detectServerIP() {
        try {
            // Găsește prima interfață non-loopback
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();

                // Ignoră interfețe inactive sau loopback
                if (iface.isLoopback() || !iface.isUp()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();

                    // Caută adrese IPv4 non-loopback
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        serverIP = addr.getHostAddress();
                        LOGGER.info("Detected server IP: " + serverIP);
                        return;
                    }
                }
            }

            // Fallback la localhost dacă nu găsim altceva
            serverIP = InetAddress.getLocalHost().getHostAddress();
            LOGGER.warning("Could not detect LAN IP, using: " + serverIP);

        } catch (Exception e) {
            serverIP = "localhost";
            LOGGER.severe("Error detecting IP: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            // IMPORTANT: Bind pe 0.0.0.0 pentru a accepta conexiuni din orice sursă
            InetAddress bindAddr = InetAddress.getByName(BIND_ADDRESS);
            serverSocket = new ServerSocket(port, 50, bindAddr);

            LOGGER.info("═══════════════════════════════════════════════════");
            LOGGER.info("  Room Registry Server STARTED");
            LOGGER.info("  Port: " + port);
            LOGGER.info("  Binding: " + BIND_ADDRESS + " (all interfaces)");
            LOGGER.info("  Server IP: " + serverIP);
            LOGGER.info("  Ready to accept connections from LAN");
            LOGGER.info("═══════════════════════════════════════════════════");

            // Pornește task-ul de curățare periodică
            startCleanupTask();

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    String clientIP = clientSocket.getInetAddress().getHostAddress();
                    LOGGER.info("Client connected from: " + clientIP);

                    // Handle client in separate thread
                    new ClientHandler(clientSocket).start();
                } catch (SocketException e) {
                    if (running) {
                        LOGGER.warning("Socket error: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.severe("Server error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }

    private void startCleanupTask() {
        cleanupScheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            List<String> toRemove = new ArrayList<>();

            synchronized (registeredRooms) {
                for (Map.Entry<String, RegisteredRoom> entry : registeredRooms.entrySet()) {
                    long timeSinceHeartbeat = now - entry.getValue().lastHeartbeat;

                    if (timeSinceHeartbeat > ROOM_TIMEOUT_MS) {
                        toRemove.add(entry.getKey());
                    }
                }

                for (String key : toRemove) {
                    RegisteredRoom removed = registeredRooms.remove(key);
                    LOGGER.warning("⏱️ Removed inactive room (timeout): " +
                            removed.room.getRoomName() + " at " + key);
                }
            }

            if (!toRemove.isEmpty()) {
                LOGGER.info("Active rooms: " + registeredRooms.size());
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    public void shutdown() {
        running = false;
        cleanupScheduler.shutdown();

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            LOGGER.info("Room Registry Server stopped");
        } catch (IOException e) {
            LOGGER.warning("Error closing server: " + e.getMessage());
        }
    }

    /**
     * Returnează IP-ul serverului pentru a fi comunicat clienților.
     */
    public String getServerIP() {
        return serverIP;
    }

    /**
     * Returnează numărul de camere active.
     */
    public int getActiveRoomCount() {
        return registeredRooms.size();
    }

    private class ClientHandler extends Thread {
        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                socket.setSoTimeout(5000); // 5 second timeout pentru read
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                String request = in.readLine();
                if (request == null) {
                    LOGGER.warning("Received null request from " + socket.getInetAddress());
                    return;
                }

                LOGGER.fine("Received request: " + request);
                handleRequest(request);

            } catch (IOException e) {
                LOGGER.warning("Client handler error: " + e.getMessage());
            } finally {
                closeConnection();
            }
        }

        private void handleRequest(String request) {
            String[] parts = request.split("\\|");
            if (parts.length < 1) {
                out.println("ERROR|Invalid request format");
                return;
            }

            String command = parts[0];

            switch (command) {
                case "REGISTER":
                    handleRegister(parts);
                    break;
                case "UNREGISTER":
                    handleUnregister(parts);
                    break;
                case "LIST":
                    handleList();
                    break;
                case "HEARTBEAT":
                    handleHeartbeat(parts);
                    break;
                case "PING":
                    handlePing();
                    break;
                case "INFO":
                    handleInfo();
                    break;
                default:
                    out.println("ERROR|Unknown command: " + command);
                    LOGGER.warning("Unknown command received: " + command);
            }
        }

        private void handleRegister(String[] parts) {
            // Format: REGISTER|roomName|hostName|hostIP|tcpPort
            if (parts.length != 5) {
                out.println("ERROR|Invalid REGISTER format (expected 5 parts, got " + parts.length + ")");
                return;
            }

            String roomName = parts[1];
            String hostName = parts[2];
            String hostIP = parts[3];
            int tcpPort;

            try {
                tcpPort = Integer.parseInt(parts[4]);

                // Validare port
                if (tcpPort < 1024 || tcpPort > 65535) {
                    out.println("ERROR|Port must be between 1024 and 65535");
                    return;
                }
            } catch (NumberFormatException e) {
                out.println("ERROR|Invalid port number: " + parts[4]);
                return;
            }

            // Validare IP
            if (!isValidIP(hostIP)) {
                out.println("ERROR|Invalid IP address: " + hostIP);
                return;
            }

            String roomKey = hostIP + ":" + tcpPort;

            // Verifică dacă camera există deja
            synchronized (registeredRooms) {
                if (registeredRooms.containsKey(roomKey)) {
                    LOGGER.warning("Room already exists at " + roomKey + ", updating...");
                }

                ChatRoom room = new ChatRoom(roomName, hostName, hostIP, tcpPort);
                registeredRooms.put(roomKey, new RegisteredRoom(room));
            }

            LOGGER.info("✓ Registered room: '" + roomName + "' by " + hostName +
                    " at " + hostIP + ":" + tcpPort +
                    " (Total: " + registeredRooms.size() + " rooms)");
            out.println("OK|Room registered successfully");
        }

        private void handleUnregister(String[] parts) {
            // Format: UNREGISTER|hostIP|tcpPort
            if (parts.length != 3) {
                out.println("ERROR|Invalid UNREGISTER format");
                return;
            }

            String hostIP = parts[1];
            String tcpPort = parts[2];
            String roomKey = hostIP + ":" + tcpPort;

            synchronized (registeredRooms) {
                RegisteredRoom removed = registeredRooms.remove(roomKey);
                if (removed != null) {
                    LOGGER.info("✓ Unregistered room: '" + removed.room.getRoomName() +
                            "' at " + roomKey +
                            " (Remaining: " + registeredRooms.size() + " rooms)");
                    out.println("OK|Room unregistered");
                } else {
                    LOGGER.warning("Attempted to unregister non-existent room: " + roomKey);
                    out.println("ERROR|Room not found");
                }
            }
        }

        private void handleList() {
            synchronized (registeredRooms) {
                int roomCount = registeredRooms.size();

                if (roomCount == 0) {
                    out.println("LIST|0");
                    LOGGER.fine("Sent empty room list");
                    return;
                }

                StringBuilder response = new StringBuilder("LIST|" + roomCount);

                for (RegisteredRoom regRoom : registeredRooms.values()) {
                    ChatRoom room = regRoom.room;
                    response.append("|")
                            .append(room.getRoomName()).append(";")
                            .append(room.getHostName()).append(";")
                            .append(room.getHostIP()).append(";")
                            .append(room.getTcpPort());
                }

                out.println(response.toString());
                LOGGER.fine("Sent room list: " + roomCount + " rooms to " +
                        socket.getInetAddress().getHostAddress());
            }
        }

        private void handleHeartbeat(String[] parts) {
            // Format: HEARTBEAT|hostIP|tcpPort
            if (parts.length != 3) {
                out.println("ERROR|Invalid HEARTBEAT format");
                return;
            }

            String hostIP = parts[1];
            String tcpPort = parts[2];
            String roomKey = hostIP + ":" + tcpPort;

            synchronized (registeredRooms) {
                RegisteredRoom room = registeredRooms.get(roomKey);
                if (room != null) {
                    room.updateHeartbeat();
                    out.println("OK|Heartbeat received");
                    LOGGER.finest("💓 Heartbeat from " + roomKey + " (room: " + room.room.getRoomName() + ")");
                } else {
                    LOGGER.warning("Heartbeat from unregistered room: " + roomKey);
                    out.println("ERROR|Room not registered");
                }
            }
        }

        private void handlePing() {
            out.println("PONG|" + serverIP);
            LOGGER.finest("PING from " + socket.getInetAddress().getHostAddress());
        }

        private void handleInfo() {
            synchronized (registeredRooms) {
                StringBuilder info = new StringBuilder("INFO|");
                info.append("rooms=").append(registeredRooms.size()).append("|");
                info.append("serverIP=").append(serverIP).append("|");
                info.append("port=").append(port);
                out.println(info.toString());
            }
        }

        private boolean isValidIP(String ip) {
            try {
                InetAddress.getByName(ip);
                return true;
            } catch (UnknownHostException e) {
                return false;
            }
        }

        private void closeConnection() {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) {
                LOGGER.warning("Error closing connection: " + e.getMessage());
            }
        }
    }

    private static class RegisteredRoom {
        final ChatRoom room;
        long lastHeartbeat;
        final long registeredAt;

        RegisteredRoom(ChatRoom room) {
            this.room = room;
            this.lastHeartbeat = System.currentTimeMillis();
            this.registeredAt = System.currentTimeMillis();
        }

        void updateHeartbeat() {
            this.lastHeartbeat = System.currentTimeMillis();
        }

        long getUptime() {
            return System.currentTimeMillis() - registeredAt;
        }
    }

    /**
     * Utility pentru a afișa informații despre toate camerele active.
     */
    public void printRoomStatus() {
        synchronized (registeredRooms) {
            System.out.println("\n═══════════════════════════════════════");
            System.out.println("  Active Rooms: " + registeredRooms.size());
            System.out.println("═══════════════════════════════════════");

            if (registeredRooms.isEmpty()) {
                System.out.println("  No active rooms");
            } else {
                for (Map.Entry<String, RegisteredRoom> entry : registeredRooms.entrySet()) {
                    RegisteredRoom regRoom = entry.getValue();
                    ChatRoom room = regRoom.room;
                    long uptime = regRoom.getUptime() / 1000; // seconds
                    long lastHB = (System.currentTimeMillis() - regRoom.lastHeartbeat) / 1000;

                    System.out.printf("  • %s\n", room.getRoomName());
                    System.out.printf("    Host: %s @ %s:%d\n",
                            room.getHostName(), room.getHostIP(), room.getTcpPort());
                    System.out.printf("    Uptime: %ds | Last HB: %ds ago\n", uptime, lastHB);
                }
            }
            System.out.println("═══════════════════════════════════════\n");
        }
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number, using default: " + DEFAULT_PORT);
            }
        }

        RoomRegistryServer server = new RoomRegistryServer(port);
        server.start();

        // Status monitor thread
        Thread statusMonitor = new Thread(() -> {
            while (server.isAlive()) {
                try {
                    Thread.sleep(60000); // Every minute
                    server.printRoomStatus();
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        statusMonitor.setDaemon(true);
        statusMonitor.start();

        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🛑 Shutting down server...");
            server.shutdown();
            try {
                server.join(2000);
            } catch (InterruptedException e) {
                // Ignore
            }
            System.out.println("✓ Server stopped");
        }));
    }
}