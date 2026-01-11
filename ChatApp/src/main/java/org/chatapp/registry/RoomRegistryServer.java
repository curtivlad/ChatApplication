package org.chatapp.registry;

import org.chatapp.entities.ChatRoom;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 * Server centralizat pentru înregistrarea și descoperirea camerelor de chat.
 * Înlocuiește sistemul UDP broadcast cu un model client-server.
 */
public class RoomRegistryServer extends Thread {
    private static final Logger LOGGER = Logger.getLogger(RoomRegistryServer.class.getName());
    private static final int DEFAULT_PORT = 7777;
    private static final long ROOM_TIMEOUT_MS = 30000; // 30 secunde

    private final int port;
    private ServerSocket serverSocket;
    private final Map<String, RegisteredRoom> registeredRooms;
    private final ScheduledExecutorService cleanupScheduler;
    private volatile boolean running;

    public RoomRegistryServer(int port) {
        this.port = port;
        this.registeredRooms = new ConcurrentHashMap<>();
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
        this.running = true;

        setupLogging();
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

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            LOGGER.info("Room Registry Server started on port " + port);

            // Pornește task-ul de curățare periodică
            startCleanupTask();

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    LOGGER.fine("Client connected: " + clientSocket.getInetAddress());

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
                    if (now - entry.getValue().lastHeartbeat > ROOM_TIMEOUT_MS) {
                        toRemove.add(entry.getKey());
                    }
                }

                for (String key : toRemove) {
                    RegisteredRoom removed = registeredRooms.remove(key);
                    LOGGER.info("Removed inactive room: " + removed.room.getRoomName());
                }
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
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                String request = in.readLine();
                if (request == null) return;

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
            if (parts.length < 1) return;

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
                default:
                    out.println("ERROR|Unknown command");
            }
        }

        private void handleRegister(String[] parts) {
            // Format: REGISTER|roomName|hostName|hostIP|tcpPort
            if (parts.length != 5) {
                out.println("ERROR|Invalid REGISTER format");
                return;
            }

            String roomName = parts[1];
            String hostName = parts[2];
            String hostIP = parts[3];
            int tcpPort;

            try {
                tcpPort = Integer.parseInt(parts[4]);
            } catch (NumberFormatException e) {
                out.println("ERROR|Invalid port number");
                return;
            }

            String roomKey = hostIP + ":" + tcpPort;
            ChatRoom room = new ChatRoom(roomName, hostName, hostIP, tcpPort);

            synchronized (registeredRooms) {
                registeredRooms.put(roomKey, new RegisteredRoom(room));
            }

            LOGGER.info("Registered room: " + roomName + " by " + hostName + " at " + hostIP + ":" + tcpPort);
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
                    LOGGER.info("Unregistered room: " + removed.room.getRoomName());
                    out.println("OK|Room unregistered");
                } else {
                    out.println("ERROR|Room not found");
                }
            }
        }

        private void handleList() {
            synchronized (registeredRooms) {
                if (registeredRooms.isEmpty()) {
                    out.println("LIST|0");
                    return;
                }

                StringBuilder response = new StringBuilder("LIST|" + registeredRooms.size());

                for (RegisteredRoom regRoom : registeredRooms.values()) {
                    ChatRoom room = regRoom.room;
                    response.append("|")
                            .append(room.getRoomName()).append(";")
                            .append(room.getHostName()).append(";")
                            .append(room.getHostIP()).append(";")
                            .append(room.getTcpPort());
                }

                out.println(response.toString());
                LOGGER.fine("Sent room list: " + registeredRooms.size() + " rooms");
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
                    LOGGER.finest("Heartbeat from " + roomKey);
                } else {
                    out.println("ERROR|Room not registered");
                }
            }
        }

        private void closeConnection() {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                LOGGER.warning("Error closing connection: " + e.getMessage());
            }
        }
    }

    private static class RegisteredRoom {
        final ChatRoom room;
        long lastHeartbeat;

        RegisteredRoom(ChatRoom room) {
            this.room = room;
            this.lastHeartbeat = System.currentTimeMillis();
        }

        void updateHeartbeat() {
            this.lastHeartbeat = System.currentTimeMillis();
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

        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down server...");
            server.shutdown();
        }));
    }
}