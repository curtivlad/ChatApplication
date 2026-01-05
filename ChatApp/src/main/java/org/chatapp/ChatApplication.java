package org.chatapp;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.io.IOException;
import java.util.*;

import org.chatapp.entieties.*;
import org.chatapp.server.*;

public class ChatApplication extends Application {
    private static final int TCP_PORT = 9999;

    private String userName;
    private UDPListener udpListener;
    private UDPBroadcaster udpBroadcaster;
    private ChatServer chatServer;
    private ChatClient chatClient;

    private ListView<ChatRoom> roomListView;
    private TextArea chatArea;
    private TextField messageField;
    private Button sendButton;
    private Label statusLabel;

    private final Map<String, ChatRoom> discoveredRooms = new HashMap<>();

    private Stage primaryStage;
    private Scene welcomeScene;
    private Scene joinScene;
    private Scene chatScene;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("LAN Chat");

        // Crează scene-urile
        welcomeScene = createWelcomeScene();
        joinScene = createJoinScene();
        chatScene = createChatScene();

        // Arată welcome scene
        primaryStage.setScene(welcomeScene);
        primaryStage.show();

        // Pornește listener-ul UDP
        startUDPListener();

        primaryStage.setOnCloseRequest(e -> cleanup());
    }

    private Scene createWelcomeScene() {
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.setStyle("-fx-background-color: #f5f5f5;");

        Label titleLabel = new Label("Welcome to LAN Chat");
        titleLabel.setStyle("-fx-font-size: 32px; -fx-font-weight: bold;");

        // Name input section
        VBox nameBox = new VBox(10);
        nameBox.setAlignment(Pos.CENTER);
        nameBox.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 5; -fx-padding: 20;");

        Label nameLabel = new Label("Enter your name:");
        nameLabel.setStyle("-fx-font-size: 14px;");

        TextField nameField = new TextField();
        nameField.setPromptText("Enter your name");
        nameField.setText("User");
        nameField.setStyle("-fx-font-size: 14px; -fx-padding: 8;");
        nameField.setPrefWidth(200);

        nameBox.getChildren().addAll(nameLabel, nameField);

        Label subtitleLabel = new Label("Choose an option:");
        subtitleLabel.setStyle("-fx-font-size: 16px;");

        HBox buttonBox = new HBox(20);
        buttonBox.setAlignment(Pos.CENTER);

        Button hostButton = new Button("Host a Room");
        hostButton.setStyle("-fx-font-size: 14px; -fx-padding: 10px 30px;");
        hostButton.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                name = "User";
            }
            userName = name;
            primaryStage.setTitle("LAN Chat - " + userName);
            showHostDialog();
        });

        Button joinButton = new Button("Join a Room");
        joinButton.setStyle("-fx-font-size: 14px; -fx-padding: 10px 30px;");
        joinButton.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                name = "User";
            }
            userName = name;
            primaryStage.setTitle("LAN Chat - " + userName);
            primaryStage.setScene(joinScene);
        });

        buttonBox.getChildren().addAll(hostButton, joinButton);
        root.getChildren().addAll(titleLabel, nameBox, subtitleLabel, buttonBox);

        return new Scene(root, 900, 600);
    }

    private Scene createJoinScene() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        root.setStyle("-fx-background-color: #f5f5f5;");

        Label titleLabel = new Label("Available Chat Rooms");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        roomListView = new ListView<>();
        roomListView.setPlaceholder(new Label("No rooms available.\nWaiting for servers..."));
        VBox.setVgrow(roomListView, Priority.ALWAYS);

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(10));

        Button joinButton = new Button("Join Selected");
        joinButton.setDisable(true);
        joinButton.setOnAction(e -> joinRoom());

        Button backButton = new Button("Back");
        backButton.setOnAction(e -> primaryStage.setScene(welcomeScene));

        roomListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> joinButton.setDisable(newVal == null)
        );

        buttonBox.getChildren().addAll(joinButton, backButton);
        root.getChildren().addAll(titleLabel, roomListView, buttonBox);

        return new Scene(root, 900, 600);
    }

    private Scene createChatScene() {
        // Layout principal
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // Chat area
        Label chatLabel = new Label("Chat");
        chatLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);

        // Message input
        HBox inputBox = new HBox(10);
        messageField = new TextField();
        messageField.setPromptText("Type your message...");
        HBox.setHgrow(messageField, Priority.ALWAYS);

        sendButton = new Button("Send");
        sendButton.setOnAction(e -> sendMessage());

        messageField.setOnAction(e -> sendMessage());

        inputBox.getChildren().addAll(messageField, sendButton);

        VBox chatPanel = new VBox(10, chatLabel, chatArea, inputBox);
        VBox.setVgrow(chatArea, Priority.ALWAYS);

        // Status bar
        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 5;");

        root.setCenter(chatPanel);
        root.setBottom(statusLabel);

        return new Scene(root, 900, 600);
    }

    private void startUDPListener() {
        udpListener = new UDPListener(room -> Platform.runLater(() -> {
            String key = room.getHostIP() + ":" + room.getTcpPort();
            if (!discoveredRooms.containsKey(key)) {
                discoveredRooms.put(key, room);
                if (roomListView != null) {
                    roomListView.getItems().add(room);
                }
            }
        }));
        udpListener.start();
    }

    private void showHostDialog() {
        TextInputDialog dialog = new TextInputDialog("My Room");
        dialog.setTitle("Host Room");
        dialog.setHeaderText("Create a new chat room");
        dialog.setContentText("Room name:");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && !result.get().trim().isEmpty()) {
            String roomName = result.get().trim();

            try {
                // Pornește serverul de chat
                chatServer = new ChatServer(TCP_PORT, message -> Platform.runLater(() -> appendMessage(message)));
                chatServer.start();

                // Pornește broadcaster-ul UDP
                udpBroadcaster = new UDPBroadcaster(roomName, userName, TCP_PORT);
                udpBroadcaster.start();

                statusLabel.setText("Hosting room: " + roomName);
                appendMessage("=== You are hosting '" + roomName + "' ===");
                appendMessage("Waiting for clients to join...");

                // Mergi la scene-ul de chat
                primaryStage.setScene(chatScene);
            } catch (Exception e) {
                showError("Failed to host room: " + e.getMessage());
            }
        }
    }

    private void joinRoom() {
        ChatRoom selectedRoom = roomListView.getSelectionModel().getSelectedItem();
        if (selectedRoom != null) {
            try {
                chatClient = new ChatClient(
                        selectedRoom.getHostIP(),
                        selectedRoom.getTcpPort(),
                        message -> Platform.runLater(() -> appendMessage(message))
                );
                chatClient.start();

                // Trimite mesaj de join
                chatClient.sendMessage(userName + " has joined the chat");

                statusLabel.setText("Connected to: " + selectedRoom.getRoomName() +
                        " (Host: " + selectedRoom.getHostName() + ")");
                appendMessage("=== Connected to '" + selectedRoom.getRoomName() + "' ===");

                // Mergi la scene-ul de chat
                primaryStage.setScene(chatScene);

            } catch (IOException e) {
                showError("Failed to connect: " + e.getMessage());
            }
        }
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            String fullMessage = userName + ": " + message;

            if (chatServer != null) {
                chatServer.broadcastMessage(fullMessage);
            } else if (chatClient != null) {
                chatClient.sendMessage(fullMessage);
            }

            messageField.clear();
        }
    }

    private void appendMessage(String message) {
        chatArea.appendText(message + "\n");
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void cleanup() {
        System.out.println("Cleaning up resources...");

        try {
            if (udpListener != null) {
                udpListener.stopListening();
                // Așteaptă thread-ul să se termine cu timeout
                udpListener.join(2000);
            }
        } catch (InterruptedException e) {
            System.err.println("Error waiting for UDP listener: " + e.getMessage());
        }

        try {
            if (udpBroadcaster != null) {
                udpBroadcaster.stopBroadcasting();
                // Așteaptă thread-ul să se termine cu timeout
                udpBroadcaster.join(2000);
            }
        } catch (InterruptedException e) {
            System.err.println("Error waiting for UDP broadcaster: " + e.getMessage());
        }

        if (chatServer != null) {
            chatServer.stopServer();
        }

        if (chatClient != null) {
            chatClient.disconnect();
        }

        System.out.println("Cleanup completed");
    }

    public static void main(String[] args) {
        launch(args);
    }
}