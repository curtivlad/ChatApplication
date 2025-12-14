package org.chatapp.server;

import java.io.*;
import java.net.*;
import java.util.function.Consumer;

public class ChatClient extends Thread {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private boolean running;
    private Consumer<String> onMessageReceived;

    public ChatClient(String serverIP, int serverPort, Consumer<String> onMessageReceived)
            throws IOException {
        this.onMessageReceived = onMessageReceived;
        this.running = true;

        socket = new Socket(serverIP, serverPort);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);

        System.out.println("Connected to server at " + serverIP + ":" + serverPort);
    }

    @Override
    public void run() {
        try {
            String message;
            while (running && (message = in.readLine()) != null) {
                System.out.println("Received: " + message);
                if (onMessageReceived != null) {
                    onMessageReceived.accept(message);
                }
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Connection error: " + e.getMessage());
            }
        } finally {
            disconnect();
        }
    }

    public void sendMessage(String message) {
        if (out != null && !socket.isClosed()) {
            out.println(message);
        }
    }

    public void disconnect() {
        running = false;
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Error disconnecting: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return socket != null && !socket.isClosed() && running;
    }
}