package org.chatapp.entities;

import java.util.Objects;

/**
 * Reprezintă o cameră de chat cu informații despre host și conexiune.
 */
public class ChatRoom {
    private String roomName;
    private String hostName;
    private String hostIP;
    private int tcpPort;
    private long discoveredAt;

    public ChatRoom(String roomName, String hostName, String hostIP, int tcpPort) {
        validateParameters(roomName, hostName, hostIP, tcpPort);

        this.roomName = roomName;
        this.hostName = hostName;
        this.hostIP = hostIP;
        this.tcpPort = tcpPort;
        this.discoveredAt = System.currentTimeMillis();
    }

    private void validateParameters(String roomName, String hostName, String hostIP, int tcpPort) {
        if (roomName == null || roomName.trim().isEmpty()) {
            throw new IllegalArgumentException("Room name cannot be empty");
        }
        if (hostName == null || hostName.trim().isEmpty()) {
            throw new IllegalArgumentException("Host name cannot be empty");
        }
        if (hostIP == null || hostIP.trim().isEmpty()) {
            throw new IllegalArgumentException("Host IP cannot be empty");
        }
        if (tcpPort < 1024 || tcpPort > 65535) {
            throw new IllegalArgumentException("TCP port must be between 1024 and 65535");
        }
    }

    // Getters
    public String getRoomName() {
        return roomName;
    }

    public String getHostName() {
        return hostName;
    }

    public String getHostIP() {
        return hostIP;
    }

    public int getTcpPort() {
        return tcpPort;
    }

    public long getDiscoveredAt() {
        return discoveredAt;
    }

    // Setters
    public void setRoomName(String roomName) {
        if (roomName != null && !roomName.trim().isEmpty()) {
            this.roomName = roomName;
        }
    }

    public void setHostName(String hostName) {
        if (hostName != null && !hostName.trim().isEmpty()) {
            this.hostName = hostName;
        }
    }

    public void setHostIP(String hostIP) {
        if (hostIP != null && !hostIP.trim().isEmpty()) {
            this.hostIP = hostIP;
        }
    }

    public void setTcpPort(int tcpPort) {
        if (tcpPort >= 1024 && tcpPort <= 65535) {
            this.tcpPort = tcpPort;
        }
    }

    /**
     * Returnează un identificator unic pentru cameră bazat pe IP și port.
     */
    public String getUniqueKey() {
        return hostIP + ":" + tcpPort;
    }

    /**
     * Returnează adresa completă de conexiune.
     */
    public String getConnectionAddress() {
        return hostIP + ":" + tcpPort;
    }

    @Override
    public String toString() {
        return roomName + " (Host: " + hostName + " @ " + hostIP + ")";
    }

    public String toDetailedString() {
        return String.format("Room: %s | Host: %s | Address: %s:%d",
                roomName, hostName, hostIP, tcpPort);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatRoom chatRoom = (ChatRoom) o;
        return tcpPort == chatRoom.tcpPort &&
                Objects.equals(hostIP, chatRoom.hostIP);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostIP, tcpPort);
    }
}