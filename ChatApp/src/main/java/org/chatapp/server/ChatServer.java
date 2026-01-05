package org.chatapp.server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.function.Consumer;

public class ChatServer extends Thread {
    private ServerSocket serverSocket;
    private List<ClientHandler> clients;
    private boolean running;
    private Consumer<String> onMessageReceived;
    private int port;

    public ChatServer(int port, Consumer<String> onMessageReceived) {
        this.port = port;
        this.onMessageReceived = onMessageReceived;
        this.clients = new ArrayList<>();
        this.running = true;
        setDaemon(true); // Marchez ca daemon thread pentru a permite terminarea aplicatiei
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Chat server started on port " + port);

            while (running) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket);
                clients.add(handler);
                handler.start();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Server error: " + e.getMessage());
            }
        }
    }

    public void broadcastMessage(String message) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.sendMessage(message);
            }
        }
        if (onMessageReceived != null) {
            onMessageReceived.accept(message);
        }
    }

    public void stopServer() {
        running = false;
        try {
            for (ClientHandler client : clients) {
                client.close();
            }
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server: " + e.getMessage());
        }
    }

    private class ClientHandler extends Thread {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
            } catch (IOException e) {
                System.err.println("Error creating client handler: " + e.getMessage());
            }
        }

        @Override
        public void run() {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    System.out.println("Received: " + message);
                    broadcastMessage(message);
                }
            } catch (IOException e) {
                System.err.println("Client disconnected: " + e.getMessage());
            } finally {
                close();
                synchronized (clients) {
                    clients.remove(this);
                }
            }
        }

        public void sendMessage(String message) {
            if (out != null) {
                out.println(message);
            }
        }

        public void close() {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                System.err.println("Error closing client: " + e.getMessage());
            }
        }
    }
}