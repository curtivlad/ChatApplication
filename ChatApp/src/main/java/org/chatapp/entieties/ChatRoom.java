package org.chatapp.entieties;

public class ChatRoom {
    private String roomName;
    private String hostName;
    private String hostIP;
    private int tcpPort;

    public ChatRoom(String roomName, String hostName, String hostIP, int tcpPort) {
        this.roomName = roomName;
        this.hostName = hostName;
        this.hostIP = hostIP;
        this.tcpPort = tcpPort;
    }

    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }

    public String getHostName() { return hostName; }
    public void setHostName(String hostName) { this.hostName = hostName; }

    public String getHostIP() { return hostIP; }
    public void setHostIP(String hostIP) { this.hostIP = hostIP; }

    public int getTcpPort() { return tcpPort; }
    public void setTcpPort(int tcpPort) { this.tcpPort = tcpPort; }

    @Override
    public String toString() {
        return roomName + " (Host: " + hostName + ")";
    }
}
