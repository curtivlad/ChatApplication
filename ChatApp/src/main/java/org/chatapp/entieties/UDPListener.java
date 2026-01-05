package org.chatapp.entieties;

import java.net.*;
import java.io.IOException;
import java.util.function.Consumer;

public class UDPListener extends Thread {
    private static final int BROADCAST_PORT = 8888;
    private DatagramSocket socket;
    private boolean running;
    private final Consumer<ChatRoom> onRoomDiscovered;

    public UDPListener(Consumer<ChatRoom> onRoomDiscovered) {
        this.onRoomDiscovered = onRoomDiscovered;
        this.running = true;
        setDaemon(true); // Marchez ca daemon thread pentru a permite terminarea aplicatiei
    }

    @Override
    public void run() {
        try {
            // Permite reutilizarea adresei (SO_REUSEADDR)
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(BROADCAST_PORT));
            socket.setSoTimeout(1000); // Timeout de 1 secunda pentru receive()

            byte[] buffer = new byte[1024];

            System.out.println("Listening for chat rooms on port " + BROADCAST_PORT);

            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength());
                    String senderIP = packet.getAddress().getHostAddress();

                    // Ignoră pachetele de la propria adresă (localhost)
                    if (isLocalAddress(senderIP)) {
                        continue;
                    }

                    // Parsează mesajul: CHAT_ROOM|nume_camera|nume_host|port_tcp
                    if (message.startsWith("CHAT_ROOM|")) {
                        String[] parts = message.split("\\|");
                        if (parts.length == 4) {
                            String roomName = parts[1];
                            String hostName = parts[2];
                            int tcpPort = Integer.parseInt(parts[3]);

                            ChatRoom room = new ChatRoom(roomName, hostName, senderIP, tcpPort);

                            // Notifică UI-ul
                            if (onRoomDiscovered != null) {
                                onRoomDiscovered.accept(room);
                            }

                            System.out.println("Discovered room: " + roomName +
                                    " from " + hostName + " at " + senderIP);
                        }
                    }
                } catch (SocketTimeoutException e) {
                    // Timeout normal, continua bucla
                }
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Listener error: " + e.getMessage());
            }
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    private boolean isLocalAddress(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            // Verifică dacă este localhost sau loopback
            return addr.isLoopbackAddress() ||
                    ip.equals("127.0.0.1") ||
                    ip.equals("localhost");
        } catch (UnknownHostException e) {
            return false;
        }
    }

    public void stopListening() {
        running = false;
        if (socket != null) {
            socket.close();
        }
    }
}

