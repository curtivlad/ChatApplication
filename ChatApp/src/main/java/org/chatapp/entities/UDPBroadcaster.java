package org.chatapp.entities;

import java.net.*;
import java.io.IOException;

public class UDPBroadcaster extends Thread {
    private static final int BROADCAST_PORT = 8888;
    private DatagramSocket socket;
    private boolean running;
    private final String roomName;
    private final String hostName;
    private final int tcpPort;

    public UDPBroadcaster(String roomName, String hostName, int tcpPort) {
        this.roomName = roomName;
        this.hostName = hostName;
        this.tcpPort = tcpPort;
        this.running = true;
        setDaemon(true); // Marchez ca daemon thread pentru a permite terminarea aplicatiei
    }

    @Override
    public void run() {
        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true);

            String message = "CHAT_ROOM|" + roomName + "|" + hostName + "|" + tcpPort;
            byte[] buffer = message.getBytes();

            InetAddress broadcastAddress = InetAddress.getByName("255.255.255.255");
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length,
                    broadcastAddress, BROADCAST_PORT);

            System.out.println("Starting UDP broadcast on port " + BROADCAST_PORT);

            while (running) {
                try {
                    socket.send(packet);
                    System.out.println("Broadcasting: " + message);
                    Thread.sleep(2000); // Interval de 2 secunde
                } catch (InterruptedException e) {
                    System.out.println("Broadcaster interrupted");
                    running = false;
                    break;
                }
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Broadcast error: " + e.getMessage());
            }
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    public void stopBroadcasting() {
        running = false;
        if (socket != null) {
            socket.close();
        }
        System.out.println("UDP Broadcaster stopped");
    }
}
