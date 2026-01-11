package org.chatapp.registry;

import org.chatapp.entities.ChatRoom;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 * Client pentru comunicarea cu serverul de registry.
 * ÎMBUNĂTĂȚIT: Auto-discovery de registry servers în LAN.
 */
public class RoomRegistryClient {
    private static final Logger LOGGER = Logger.getLogger(RoomRegistryClient.class.getName());
    private static final int HEARTBEAT_INTERVAL_MS = 10000; // 10 secunde
    private static final int CONNECTION_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 5000;

    private String registryHost;
    private int registryPort;
    private ScheduledExecutorService heartbeatScheduler;
    private ChatRoom registeredRoom;
    private volatile boolean connected = false;

    public RoomRegistryClient(String registryHost, int registryPort) {
        this.registryHost = registryHost;
        this.registryPort = registryPort;
        setupLogging();
        testConnection();
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
     * Testează conexiunea la registry la inițializare.
     */
    private void testConnection() {
        connected = isRegistryAvailable();
        if (connected) {
            LOGGER.info("✓ Connected to registry at " + registryHost + ":" + registryPort);
        } else {
            LOGGER.warning("⚠️ Cannot connect to registry at " + registryHost + ":" + registryPort);
        }
    }

    /**
     * Înregistrează o cameră de chat pe serverul de registry.
     */
    public boolean registerRoom(String roomName, String hostName, String hostIP, int tcpPort) {
        String request = String.format("REGISTER|%s|%s|%s|%d",
                roomName, hostName, hostIP, tcpPort);

        String response = sendRequest(request);

        if (response != null && response.startsWith("OK")) {
            LOGGER.info("✓ Room registered successfully: " + roomName);

            // Salvează informațiile camerei pentru heartbeat
            registeredRoom = new ChatRoom(roomName, hostName, hostIP, tcpPort);
            startHeartbeat();
            connected = true;

            return true;
        } else {
            LOGGER.warning("✗ Failed to register room: " + response);
            connected = false;
            return false;
        }
    }

    /**
     * Șterge înregistrarea unei camere de pe serverul de registry.
     */
    public boolean unregisterRoom(String hostIP, int tcpPort) {
        stopHeartbeat();

        String request = String.format("UNREGISTER|%s|%d", hostIP, tcpPort);
        String response = sendRequest(request);

        if (response != null && response.startsWith("OK")) {
            LOGGER.info("✓ Room unregistered successfully");
            registeredRoom = null;
            return true;
        } else {
            LOGGER.warning("✗ Failed to unregister room: " + response);
            return false;
        }
    }

    /**
     * Obține lista tuturor camerelor disponibile.
     */
    public List<ChatRoom> listRooms() {
        List<ChatRoom> rooms = new ArrayList<>();
        String response = sendRequest("LIST");

        if (response == null) {
            LOGGER.warning("No response from registry server");
            connected = false;
            return rooms;
        }

        if (!response.startsWith("LIST")) {
            LOGGER.warning("Invalid response format: " + response);
            return rooms;
        }

        try {
            String[] parts = response.split("\\|");

            if (parts.length < 2) {
                LOGGER.warning("Response too short: " + response);
                return rooms;
            }

            int count = Integer.parseInt(parts[1]);
            LOGGER.info("Registry reports " + count + " room(s)");

            if (count == 0) {
                return rooms;
            }

            // Format: LIST|count|room1Data|room2Data|...
            // roomData: roomName;hostName;hostIP;tcpPort
            for (int i = 2; i < parts.length && i < count + 2; i++) {
                try {
                    String roomDataStr = parts[i];
                    String[] roomData = roomDataStr.split(";");

                    if (roomData.length != 4) {
                        LOGGER.warning("Invalid room data format: " + roomDataStr);
                        continue;
                    }

                    String roomName = roomData[0].trim();
                    String hostName = roomData[1].trim();
                    String hostIP = roomData[2].trim();
                    int tcpPort = Integer.parseInt(roomData[3].trim());

                    ChatRoom room = new ChatRoom(roomName, hostName, hostIP, tcpPort);
                    rooms.add(room);
                    LOGGER.fine("Parsed room: " + room.toDetailedString());

                } catch (Exception e) {
                    LOGGER.warning("Error parsing room at index " + i + ": " + e.getMessage());
                }
            }

            LOGGER.info("✓ Retrieved " + rooms.size() + " room(s) from registry");
            connected = true;

        } catch (Exception e) {
            LOGGER.severe("Error parsing room list: " + e.getMessage());
            e.printStackTrace();
            connected = false;
        }

        return rooms;
    }

    /**
     * Trimite un heartbeat pentru a menține camera activă.
     */
    private boolean sendHeartbeat() {
        if (registeredRoom == null) {
            return false;
        }

        String request = String.format("HEARTBEAT|%s|%d",
                registeredRoom.getHostIP(), registeredRoom.getTcpPort());

        String response = sendRequest(request);

        if (response != null && response.startsWith("OK")) {
            LOGGER.finest("💓 Heartbeat sent successfully");
            connected = true;
            return true;
        } else {
            LOGGER.warning("⚠️ Heartbeat failed: " + response);
            connected = false;
            return false;
        }
    }

    /**
     * Pornește trimiterea periodică de heartbeat-uri.
     */
    private void startHeartbeat() {
        if (heartbeatScheduler != null && !heartbeatScheduler.isShutdown()) {
            return;
        }

        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                boolean success = sendHeartbeat();
                if (!success) {
                    LOGGER.warning("Heartbeat failed - registry may be unreachable");
                }
            } catch (Exception e) {
                LOGGER.warning("Heartbeat error: " + e.getMessage());
                connected = false;
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);

        LOGGER.info("✓ Heartbeat started (interval: " + HEARTBEAT_INTERVAL_MS + "ms)");
    }

    /**
     * Oprește trimiterea de heartbeat-uri.
     */
    private void stopHeartbeat() {
        if (heartbeatScheduler != null && !heartbeatScheduler.isShutdown()) {
            heartbeatScheduler.shutdown();
            try {
                heartbeatScheduler.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                heartbeatScheduler.shutdownNow();
            }
            LOGGER.info("✓ Heartbeat stopped");
        }
    }

    /**
     * Trimite un ping la registry pentru a verifica conexiunea.
     */
    public String ping() {
        String response = sendRequest("PING");
        if (response != null && response.startsWith("PONG")) {
            String[] parts = response.split("\\|");
            if (parts.length > 1) {
                return parts[1]; // Registry server IP
            }
        }
        return null;
    }

    /**
     * Obține informații despre registry server.
     */
    public Map<String, String> getRegistryInfo() {
        Map<String, String> info = new HashMap<>();
        String response = sendRequest("INFO");

        if (response != null && response.startsWith("INFO")) {
            String[] parts = response.split("\\|");
            for (int i = 1; i < parts.length; i++) {
                String[] keyValue = parts[i].split("=");
                if (keyValue.length == 2) {
                    info.put(keyValue[0], keyValue[1]);
                }
            }
        }

        return info;
    }

    /**
     * Trimite o cerere la serverul de registry și returnează răspunsul.
     */
    private String sendRequest(String request) {
        Socket socket = null;
        BufferedReader in = null;
        PrintWriter out = null;

        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(registryHost, registryPort),
                    CONNECTION_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);

            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Trimite cererea
            out.println(request);
            out.flush();

            // Așteaptă răspunsul
            String response = in.readLine();

            if (response == null) {
                LOGGER.warning("Null response from registry for request: " + request);
                return null;
            }

            LOGGER.fine("Request: " + request + " → Response: " + response);
            connected = true;

            return response;

        } catch (SocketTimeoutException e) {
            LOGGER.warning("Timeout connecting to registry: " + e.getMessage());
            connected = false;
            return null;
        } catch (IOException e) {
            LOGGER.warning("Communication error with registry: " + e.getMessage());
            connected = false;
            return null;
        } finally {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) {
                LOGGER.fine("Error closing connection: " + e.getMessage());
            }
        }
    }

    /**
     * Verifică dacă serverul de registry este accesibil.
     */
    public boolean isRegistryAvailable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(registryHost, registryPort),
                    CONNECTION_TIMEOUT_MS);
            connected = true;
            return true;
        } catch (IOException e) {
            connected = false;
            return false;
        }
    }

    /**
     * Verifică dacă clientul este conectat la registry.
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Actualizează adresa registry-ului.
     */
    public void updateRegistryAddress(String host, int port) {
        // Oprește heartbeat-ul dacă era activ
        boolean wasRegistered = (registeredRoom != null);
        if (wasRegistered) {
            stopHeartbeat();
        }

        this.registryHost = host;
        this.registryPort = port;

        testConnection();

        // Reînregistrează camera dacă era înregistrată
        if (wasRegistered && connected) {
            registerRoom(registeredRoom.getRoomName(),
                    registeredRoom.getHostName(),
                    registeredRoom.getHostIP(),
                    registeredRoom.getTcpPort());
        }
    }

    /**
     * Returnează adresa curentă a registry-ului.
     */
    public String getRegistryAddress() {
        return registryHost + ":" + registryPort;
    }

    /**
     * Închide toate conexiunile și oprește serviciile.
     */
    public void shutdown() {
        if (registeredRoom != null) {
            try {
                unregisterRoom(registeredRoom.getHostIP(), registeredRoom.getTcpPort());
            } catch (Exception e) {
                LOGGER.warning("Error unregistering room during shutdown: " + e.getMessage());
            }
        }
        stopHeartbeat();
        connected = false;
        LOGGER.info("Registry client shutdown complete");
    }

    /**
     * AUTO-DISCOVERY: Caută registry servers în rețeaua locală.
     * Scanează subnet-ul curent pentru servere pe portul specificat.
     */
    public static List<String> discoverRegistryServers(int port, int timeoutMs) {
        List<String> foundServers = new ArrayList<>();

        try {
            // Obține IP-ul local
            String localIP = InetAddress.getLocalHost().getHostAddress();
            String[] parts = localIP.split("\\.");

            if (parts.length != 4) {
                LOGGER.warning("Invalid local IP format: " + localIP);
                return foundServers;
            }

            // Subnet-ul curent (ex: 192.168.1.x)
            String subnet = parts[0] + "." + parts[1] + "." + parts[2] + ".";

            LOGGER.info("Scanning subnet " + subnet + "0/24 for registry servers...");

            ExecutorService executor = Executors.newFixedThreadPool(50);
            List<Future<String>> futures = new ArrayList<>();

            // Scanează de la .1 la .254
            for (int i = 1; i < 255; i++) {
                final String host = subnet + i;

                Future<String> future = executor.submit(() -> {
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(host, port), timeoutMs);

                        // Verifică dacă răspunde la PING
                        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                        BufferedReader in = new BufferedReader(
                                new InputStreamReader(socket.getInputStream()));

                        out.println("PING");
                        socket.setSoTimeout(timeoutMs);
                        String response = in.readLine();

                        if (response != null && response.startsWith("PONG")) {
                            LOGGER.info("✓ Found registry server at " + host + ":" + port);
                            return host;
                        }
                    } catch (IOException e) {
                        // Nu e server aici, continuă
                    }
                    return null;
                });

                futures.add(future);
            }

            // Așteaptă rezultatele
            for (Future<String> future : futures) {
                try {
                    String result = future.get(timeoutMs + 1000, TimeUnit.MILLISECONDS);
                    if (result != null) {
                        foundServers.add(result);
                    }
                } catch (Exception e) {
                    // Ignore timeout/errors
                }
            }

            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);

            LOGGER.info("Discovery complete. Found " + foundServers.size() + " registry server(s)");

        } catch (Exception e) {
            LOGGER.severe("Error during registry discovery: " + e.getMessage());
        }

        return foundServers;
    }
}