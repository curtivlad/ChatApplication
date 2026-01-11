package org.chatapp.server;

import java.io.*;
import java.net.*;
import java.util.function.Consumer;
import java.util.logging.*;

/**
 * Client TCP pentru conectarea la un server de chat.
 */
public class ChatClient extends Thread {
    private static final Logger LOGGER = Logger.getLogger(ChatClient.class.getName());
    private static final int RECONNECT_DELAY_MS = 5000;
    private static final int MAX_RECONNECT_ATTEMPTS = 3;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private final String serverIP;
    private final int serverPort;
    private volatile boolean running;
    private volatile boolean connected;
    private Consumer<String> onMessageReceived;
    private Consumer<Boolean> onConnectionStatusChanged;
    private int reconnectAttempts;

    public ChatClient(String serverIP, int serverPort, Consumer<String> onMessageReceived)
            throws IOException {
        this.serverIP = serverIP;
        this.serverPort = serverPort;
        this.onMessageReceived = onMessageReceived;
        this.running = true;
        this.connected = false;
        this.reconnectAttempts = 0;

        setupLogging();
        connect();
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

    public void setOnConnectionStatusChanged(Consumer<Boolean> onConnectionStatusChanged) {
        this.onConnectionStatusChanged = onConnectionStatusChanged;
    }

    private void connect() throws IOException {
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(serverIP, serverPort), 5000);
            socket.setSoTimeout(30000); // 30 second read timeout

            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            connected = true;
            reconnectAttempts = 0;

            LOGGER.info("Connected to server at " + serverIP + ":" + serverPort);
            notifyConnectionStatus(true);

        } catch (IOException e) {
            connected = false;
            notifyConnectionStatus(false);
            throw e;
        }
    }

    @Override
    public void run() {
        while (running) {
            try {
                if (!connected) {
                    attemptReconnect();
                    continue;
                }

                String message = in.readLine();

                if (message == null) {
                    // Server closed connection
                    LOGGER.warning("Server closed connection");
                    handleDisconnection();
                    continue;
                }

                LOGGER.fine("Received: " + message);

                if (onMessageReceived != null) {
                    onMessageReceived.accept(message);
                }

            } catch (SocketTimeoutException e) {
                // Timeout is normal, continue listening
                continue;

            } catch (IOException e) {
                if (running) {
                    LOGGER.warning("Connection error: " + e.getMessage());
                    handleDisconnection();
                }
            }
        }

        cleanup();
    }

    private void handleDisconnection() {
        connected = false;
        notifyConnectionStatus(false);

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            LOGGER.warning("Error closing socket: " + e.getMessage());
        }
    }

    private void attemptReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            LOGGER.severe("Max reconnect attempts reached. Giving up.");
            running = false;
            return;
        }

        reconnectAttempts++;
        LOGGER.info("Attempting to reconnect (" + reconnectAttempts + "/" +
                MAX_RECONNECT_ATTEMPTS + ")...");

        try {
            Thread.sleep(RECONNECT_DELAY_MS);
            connect();
        } catch (IOException e) {
            LOGGER.warning("Reconnect failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    private void notifyConnectionStatus(boolean status) {
        if (onConnectionStatusChanged != null) {
            onConnectionStatusChanged.accept(status);
        }
    }

    /**
     * Trimite un mesaj către server.
     */
    public void sendMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        if (!isConnected()) {
            LOGGER.warning("Cannot send message: not connected");
            return;
        }

        try {
            out.println(message);
            LOGGER.fine("Sent: " + message);
        } catch (Exception e) {
            LOGGER.warning("Error sending message: " + e.getMessage());
            handleDisconnection();
        }
    }

    /**
     * Verifică dacă clientul este conectat la server.
     */
    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed() && running;
    }

    /**
     * Deconectează clientul de la server.
     */
    public void disconnect() {
        running = false;
        connected = false;
        cleanup();
    }

    private void cleanup() {
        notifyConnectionStatus(false);

        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();

            LOGGER.info("Disconnected from server");
        } catch (IOException e) {
            LOGGER.warning("Error during cleanup: " + e.getMessage());
        }
    }

    /**
     * Returnează adresa serverului.
     */
    public String getServerAddress() {
        return serverIP + ":" + serverPort;
    }
}