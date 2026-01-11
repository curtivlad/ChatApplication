package org.chatapp.server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.*;

/**
 * Server TCP pentru gestionarea mesajelor de chat între clienți.
 */
public class ChatServer extends Thread {
    private static final Logger LOGGER = Logger.getLogger(ChatServer.class.getName());

    private ServerSocket serverSocket;
    private final List<ClientHandler> clients;
    private final Map<String, ClientHandler> clientsByName;
    private final int port;
    private volatile boolean running;
    private Consumer<String> onMessageReceived;
    private Consumer<String> onClientConnected;
    private Consumer<String> onClientDisconnected;

    public ChatServer(int port, Consumer<String> onMessageReceived) {
        this.port = port;
        this.onMessageReceived = onMessageReceived;
        this.clients = new CopyOnWriteArrayList<>();
        this.clientsByName = new ConcurrentHashMap<>();
        this.running = true;
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

    public void setOnClientConnected(Consumer<String> onClientConnected) {
        this.onClientConnected = onClientConnected;
    }

    public void setOnClientDisconnected(Consumer<String> onClientDisconnected) {
        this.onClientDisconnected = onClientDisconnected;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
            LOGGER.info("Chat server started on port " + port);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(clientSocket);
                    clients.add(handler);
                    handler.start();

                    LOGGER.info("Client connected from: " + clientSocket.getInetAddress());

                } catch (SocketException e) {
                    if (running) {
                        LOGGER.warning("Socket error: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                LOGGER.severe("Server error: " + e.getMessage());
            }
        } finally {
            cleanup();
        }
    }

    /**
     * Transmite un mesaj către toți clienții conectați.
     */
    public void broadcastMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }

        if (onMessageReceived != null) {
            onMessageReceived.accept(message);
        }

        LOGGER.fine("Broadcast: " + message);
    }

    /**
     * Trimite un mesaj privat către un client specific.
     */
    public boolean sendPrivateMessage(String targetClientName, String message) {
        ClientHandler client = clientsByName.get(targetClientName);
        if (client != null) {
            client.sendMessage(message);
            return true;
        }
        return false;
    }

    /**
     * Returnează numărul de clienți conectați.
     */
    public int getClientCount() {
        return clients.size();
    }

    /**
     * Returnează lista cu numele clienților conectați.
     */
    public List<String> getConnectedClients() {
        return new ArrayList<>(clientsByName.keySet());
    }

    /**
     * Verifică dacă serverul rulează.
     */
    public boolean isRunning() {
        return running && serverSocket != null && !serverSocket.isClosed();
    }

    /**
     * Oprește serverul și deconectează toți clienții.
     */
    public void stopServer() {
        running = false;

        // Notifică clienții despre închidere
        broadcastMessage("SERVER: Server is shutting down...");

        cleanup();
    }

    private void cleanup() {
        for (ClientHandler client : clients) {
            client.close();
        }
        clients.clear();
        clientsByName.clear();

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            LOGGER.info("Chat server stopped");
        } catch (IOException e) {
            LOGGER.warning("Error closing server: " + e.getMessage());
        }
    }

    /**
     * Handler pentru fiecare client conectat.
     */
    private class ClientHandler extends Thread {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String clientName;
        private volatile boolean active;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.active = true;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                String message;
                while (active && (message = in.readLine()) != null) {
                    LOGGER.fine("Received from client: " + message);

                    // Verifică dacă e mesaj de JOIN
                    if (message.startsWith("JOIN:")) {
                        clientName = message.substring(5).trim();
                        clientsByName.put(clientName, this);

                        LOGGER.info("Client joined: " + clientName);

                        // Notifică callback
                        if (onClientConnected != null) {
                            onClientConnected.accept(clientName);
                        }

                        // Trimite mesaj de join către toți
                        broadcastMessage(clientName + " has joined the chat");
                        continue; // Nu procesăm mai departe acest mesaj
                    }

                    // Extrage numele clientului din primul mesaj obișnuit DOAR dacă încă nu avem nume
                    if (clientName == null && !message.startsWith("JOIN:")) {
                        extractClientName(message);
                    }

                    // Broadcast mesajul către toți clienții
                    // (nu broadcastăm mesajele JOIN, le-am procesat deja mai sus)
                    if (!message.startsWith("JOIN:")) {
                        broadcastMessage(message);
                    }
                }
            } catch (IOException e) {
                if (active) {
                    LOGGER.warning("Client connection error: " + e.getMessage());
                }
            } finally {
                handleDisconnect();
            }
        }

        private void extractClientName(String message) {
            // Presupunem că formatul este "NumeClient: mesaj" sau "NumeClient has joined"
            if (message.contains(":")) {
                clientName = message.substring(0, message.indexOf(":")).trim();
            } else if (message.contains(" has joined")) {
                clientName = message.substring(0, message.indexOf(" has joined")).trim();
            }

            if (clientName != null && !clientName.isEmpty()) {
                clientsByName.put(clientName, this);
                LOGGER.info("Client identified as: " + clientName);

                if (onClientConnected != null) {
                    onClientConnected.accept(clientName);
                }
            }
        }

        private void handleDisconnect() {
            close();

            clients.remove(this);

            if (clientName != null) {
                clientsByName.remove(clientName);

                String disconnectMsg = clientName + " has left the chat";
                broadcastMessage(disconnectMsg);

                if (onClientDisconnected != null) {
                    onClientDisconnected.accept(clientName);
                }

                LOGGER.info("Client disconnected: " + clientName);
            }
        }

        public void sendMessage(String message) {
            if (out != null && !socket.isClosed()) {
                out.println(message);
            }
        }

        public void close() {
            active = false;
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) {
                LOGGER.warning("Error closing client connection: " + e.getMessage());
            }
        }

        public String getClientName() {
            return clientName;
        }
    }
}