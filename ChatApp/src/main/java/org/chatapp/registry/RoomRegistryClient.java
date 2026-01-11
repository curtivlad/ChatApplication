package org.chatapp.registry;

import org.chatapp.entities.ChatRoom;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 * Client pentru comunicarea cu serverul de registry.
 * Gestionează înregistrarea, descoperirea și menținerea camerelor active.
 */
public class RoomRegistryClient {
    private static final Logger LOGGER = Logger.getLogger(RoomRegistryClient.class.getName());
    private static final int HEARTBEAT_INTERVAL_MS = 10000; // 10 secunde

    private final String registryHost;
    private final int registryPort;
    private ScheduledExecutorService heartbeatScheduler;
    private ChatRoom registeredRoom;

    public RoomRegistryClient(String registryHost, int registryPort) {
        this.registryHost = registryHost;
        this.registryPort = registryPort;
        setupLogging();
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
     * Înregistrează o cameră de chat pe serverul de registry.
     */
    public boolean registerRoom(String roomName, String hostName, String hostIP, int tcpPort) {
        String request = String.format("REGISTER|%s|%s|%s|%d",
                roomName, hostName, hostIP, tcpPort);

        String response = sendRequest(request);

        if (response != null && response.startsWith("OK")) {
            LOGGER.info("Room registered successfully: " + roomName);

            // Salvează informațiile camerei pentru heartbeat
            registeredRoom = new ChatRoom(roomName, hostName, hostIP, tcpPort);
            startHeartbeat();

            return true;
        } else {
            LOGGER.warning("Failed to register room: " + response);
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
            LOGGER.info("Room unregistered successfully");
            registeredRoom = null;
            return true;
        } else {
            LOGGER.warning("Failed to unregister room: " + response);
            return false;
        }
    }

    /**
     * Obține lista tuturor camerelor disponibile.
     */
    public List<ChatRoom> listRooms() {
        List<ChatRoom> rooms = new ArrayList<>();
        String response = sendRequest("LIST");

        LOGGER.info("LIST request sent, response: " + response);

        if (response == null) {
            LOGGER.warning("No response from registry server");
            return rooms;
        }

        if (!response.startsWith("LIST")) {
            LOGGER.warning("Invalid response format: " + response);
            return rooms;
        }

        try {
            String[] parts = response.split("\\|");
            LOGGER.fine("Response parts: " + parts.length);

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
            for (int i = 2; i < parts.length; i++) {
                try {
                    String roomDataStr = parts[i];
                    LOGGER.fine("Parsing room data: " + roomDataStr);

                    String[] roomData = roomDataStr.split(";");
                    if (roomData.length != 4) {
                        LOGGER.warning("Invalid room data format (expected 4 parts, got " +
                                roomData.length + "): " + roomDataStr);
                        continue;
                    }

                    String roomName = roomData[0].trim();
                    String hostName = roomData[1].trim();
                    String hostIP = roomData[2].trim();
                    int tcpPort = Integer.parseInt(roomData[3].trim());

                    ChatRoom room = new ChatRoom(roomName, hostName, hostIP, tcpPort);
                    rooms.add(room);
                    LOGGER.info("Parsed room: " + room.toDetailedString());

                } catch (Exception e) {
                    LOGGER.warning("Error parsing room at index " + i + ": " + e.getMessage());
                }
            }

            LOGGER.info("Successfully retrieved " + rooms.size() + " room(s) from registry");

        } catch (Exception e) {
            LOGGER.severe("Error parsing room list: " + e.getMessage());
            e.printStackTrace();
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
            LOGGER.finest("Heartbeat sent successfully");
            return true;
        } else {
            LOGGER.warning("Heartbeat failed: " + response);
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
                sendHeartbeat();
            } catch (Exception e) {
                LOGGER.warning("Heartbeat error: " + e.getMessage());
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);

        LOGGER.info("Heartbeat started");
    }

    /**
     * Oprește trimiterea de heartbeat-uri.
     */
    private void stopHeartbeat() {
        if (heartbeatScheduler != null && !heartbeatScheduler.isShutdown()) {
            heartbeatScheduler.shutdown();
            LOGGER.info("Heartbeat stopped");
        }
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
            socket.connect(new InetSocketAddress(registryHost, registryPort), 5000);

            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            out.println(request);
            String response = in.readLine();

            LOGGER.fine("Request: " + request + " -> Response: " + response);

            return response;

        } catch (IOException e) {
            LOGGER.severe("Communication error: " + e.getMessage());
            return null;
        } finally {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                LOGGER.warning("Error closing connection: " + e.getMessage());
            }
        }
    }

    /**
     * Verifică dacă serverul de registry este accesibil.
     */
    public boolean isRegistryAvailable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(registryHost, registryPort), 3000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Închide toate conexiunile și oprește serviciile.
     */
    public void shutdown() {
        if (registeredRoom != null) {
            unregisterRoom(registeredRoom.getHostIP(), registeredRoom.getTcpPort());
        }
        stopHeartbeat();
    }
}