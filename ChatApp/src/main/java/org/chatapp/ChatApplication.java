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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.chatapp.entities.*;
import org.chatapp.server.*;
import org.chatapp.registry.*;

/**
 * Aplicație de chat LAN cu arhitectură client-server îmbunătățită.
 * Folosește un server centralizat de registry în loc de UDP broadcast.
 */
public class ChatApplication extends Application {
    private static final int TCP_PORT_START = 9999;
    private static final int TCP_PORT_END = 10099;
    private static final String DEFAULT_REGISTRY_HOST = "localhost";
    private static final int DEFAULT_REGISTRY_PORT = 7777;

    // User info
    private String userName;

    // Registry components
    private RoomRegistryClient registryClient;
    private RoomRegistryServer embeddedRegistryServer;
    private String registryHost;
    private int registryPort;
    private boolean useEmbeddedRegistry = true; // Pornește registry automat

    // Chat components
    private ChatServer chatServer;
    private ChatClient chatClient;
    private int assignedTcpPort; // Portul atribuit pentru chat server

    // UI Components
    private ListView<ChatRoom> roomListView;
    private TextArea chatArea;
    private TextField messageField;
    private Button sendButton;
    private Label statusLabel;
    private Label clientCountLabel;
    private ListView<String> clientListView;

    // Room management
    private final Map<String, ChatRoom> discoveredRooms = new HashMap<>();
    private ScheduledExecutorService roomRefreshScheduler;

    // Scenes
    private Stage primaryStage;
    private Scene welcomeScene;
    private Scene settingsScene;
    private Scene joinScene;
    private Scene chatScene;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("LAN Chat - Enhanced");

        // Setări implicite
        registryHost = DEFAULT_REGISTRY_HOST;
        registryPort = DEFAULT_REGISTRY_PORT;

        // Pornește registry server embedded
        startEmbeddedRegistry();

        // Creează scene-urile
        welcomeScene = createWelcomeScene();
        settingsScene = createSettingsScene();
        joinScene = createJoinScene();
        chatScene = createChatScene();

        // Arată welcome scene
        primaryStage.setScene(welcomeScene);
        primaryStage.show();

        // Configurează închiderea aplicației
        primaryStage.setOnCloseRequest(e -> {
            e.consume(); // Previne închiderea automată
            handleApplicationClose();
        });
    }

    /**
     * Pornește serverul de registry embedded.
     */
    private void startEmbeddedRegistry() {
        if (!useEmbeddedRegistry) {
            return;
        }

        try {
            embeddedRegistryServer = new RoomRegistryServer(DEFAULT_REGISTRY_PORT);
            embeddedRegistryServer.setDaemon(true); // Thread daemon pentru cleanup automat
            embeddedRegistryServer.start();

            // Așteaptă puțin să se pornească serverul
            Thread.sleep(500);

            System.out.println("✓ Embedded Registry Server started on port " + DEFAULT_REGISTRY_PORT);
        } catch (Exception e) {
            System.err.println("⚠ Could not start embedded registry server: " + e.getMessage());
            System.err.println("  You may need to connect to an external registry server.");
            useEmbeddedRegistry = false;
        }
    }

    /**
     * Gestionează închiderea aplicației cu cleanup complet.
     */
    private void handleApplicationClose() {
        // Rulăm dialog-ul pe JavaFX thread
        Platform.runLater(() -> {
            Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
            confirmDialog.setTitle("Confirm Exit");
            confirmDialog.setHeaderText("Are you sure you want to exit?");
            confirmDialog.setContentText("All active connections will be closed.");

            Optional<ButtonType> result = confirmDialog.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                // Facem cleanup într-un thread separat pentru a nu bloca UI
                new Thread(() -> {
                    try {
                        cleanup();
                        // Așteptăm puțin să se termine cleanup-ul
                        Thread.sleep(500);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        // Forțăm închiderea completă
                        Platform.exit();
                        System.exit(0);
                    }
                }).start();
            }
        });
    }

    private Scene createWelcomeScene() {
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #667eea 0%, #764ba2 100%);");

        // Title
        Label titleLabel = new Label("LAN Chat Enhanced");
        titleLabel.setStyle("-fx-font-size: 42px; -fx-font-weight: bold; -fx-text-fill: white;");

        Label subtitleLabel = new Label("Connect. Chat. Collaborate.");
        subtitleLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #e0e0e0;");

        // Name input section
        VBox nameBox = new VBox(10);
        nameBox.setAlignment(Pos.CENTER);
        nameBox.setMaxWidth(400);
        nameBox.setStyle("-fx-background-color: rgba(255, 255, 255, 0.95); " +
                "-fx-background-radius: 10; -fx-padding: 30;");

        Label nameLabel = new Label("Enter your name:");
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        TextField nameField = new TextField();
        nameField.setPromptText("Your name");
        nameField.setText("User");
        nameField.setStyle("-fx-font-size: 14px; -fx-padding: 10;");
        nameField.setPrefWidth(300);

        nameBox.getChildren().addAll(nameLabel, nameField);

        // Buttons
        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);

        Button hostButton = createStyledButton("🏠 Host a Room", "#4CAF50");
        Button joinButton = createStyledButton("🚪 Join a Room", "#2196F3");
        Button settingsButton = createStyledButton("⚙️ Settings", "#FF9800");

        hostButton.setOnAction(e -> {
            setUserName(nameField.getText());
            showHostDialog();
        });

        joinButton.setOnAction(e -> {
            setUserName(nameField.getText());
            initializeRegistry();
            primaryStage.setScene(joinScene);
            startRoomDiscovery();
        });

        settingsButton.setOnAction(e -> {
            primaryStage.setScene(settingsScene);
        });

        buttonBox.getChildren().addAll(hostButton, joinButton, settingsButton);

        VBox.setMargin(titleLabel, new Insets(0, 0, 10, 0));
        VBox.setMargin(nameBox, new Insets(20, 0, 20, 0));

        root.getChildren().addAll(titleLabel, subtitleLabel, nameBox, buttonBox);

        return new Scene(root, 900, 600);
    }

    private Scene createSettingsScene() {
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #f5f5f5;");

        Label titleLabel = new Label("Registry Server Settings");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        // Checkbox pentru embedded registry
        CheckBox embeddedCheckBox = new CheckBox("Use Embedded Registry Server (Automatic)");
        embeddedCheckBox.setSelected(useEmbeddedRegistry);
        embeddedCheckBox.setStyle("-fx-font-size: 13px;");

        Label infoLabel = new Label("When enabled, the application starts its own registry server automatically.");
        infoLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        infoLabel.setWrapText(true);

        Separator separator = new Separator();

        Label externalLabel = new Label("External Registry Server (Advanced)");
        externalLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setAlignment(Pos.CENTER);

        Label hostLabel = new Label("Registry Host:");
        TextField hostField = new TextField(registryHost);
        hostField.setPromptText("localhost or IP address");
        hostField.setDisable(useEmbeddedRegistry);

        Label portLabel = new Label("Registry Port:");
        TextField portField = new TextField(String.valueOf(registryPort));
        portField.setPromptText("7777");
        portField.setDisable(useEmbeddedRegistry);

        Button testButton = new Button("Test Connection");
        testButton.setDisable(useEmbeddedRegistry);
        Label testResultLabel = new Label("");

        // Event handler pentru checkbox
        embeddedCheckBox.setOnAction(e -> {
            boolean embedded = embeddedCheckBox.isSelected();
            hostField.setDisable(embedded);
            portField.setDisable(embedded);
            testButton.setDisable(embedded);

            if (embedded) {
                hostField.setText("localhost");
                portField.setText(String.valueOf(DEFAULT_REGISTRY_PORT));
            }
        });

        testButton.setOnAction(e -> {
            String host = hostField.getText().trim();
            int port;
            try {
                port = Integer.parseInt(portField.getText().trim());
                RoomRegistryClient testClient = new RoomRegistryClient(host, port);

                if (testClient.isRegistryAvailable()) {
                    testResultLabel.setText("✓ Connection successful!");
                    testResultLabel.setStyle("-fx-text-fill: green;");
                } else {
                    testResultLabel.setText("✗ Cannot connect to registry server");
                    testResultLabel.setStyle("-fx-text-fill: red;");
                }
            } catch (NumberFormatException ex) {
                testResultLabel.setText("✗ Invalid port number");
                testResultLabel.setStyle("-fx-text-fill: red;");
            }
        });

        grid.add(hostLabel, 0, 0);
        grid.add(hostField, 1, 0);
        grid.add(portLabel, 0, 1);
        grid.add(portField, 1, 1);
        grid.add(testButton, 0, 2);
        grid.add(testResultLabel, 1, 2);

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);

        Button saveButton = new Button("Save");
        Button cancelButton = new Button("Cancel");

        saveButton.setOnAction(e -> {
            try {
                boolean wasEmbedded = useEmbeddedRegistry;
                useEmbeddedRegistry = embeddedCheckBox.isSelected();

                if (!useEmbeddedRegistry) {
                    registryHost = hostField.getText().trim();
                    registryPort = Integer.parseInt(portField.getText().trim());
                } else {
                    registryHost = "localhost";
                    registryPort = DEFAULT_REGISTRY_PORT;
                }

                // Dacă s-a schimbat modul, repornește registry
                if (wasEmbedded != useEmbeddedRegistry) {
                    if (useEmbeddedRegistry) {
                        startEmbeddedRegistry();
                    } else if (embeddedRegistryServer != null) {
                        embeddedRegistryServer.shutdown();
                        embeddedRegistryServer = null;
                    }
                }

                showInfo("Settings saved successfully!\n" +
                        (useEmbeddedRegistry ? "Using embedded registry server." :
                                "Using external registry at " + registryHost + ":" + registryPort));
                primaryStage.setScene(welcomeScene);
            } catch (NumberFormatException ex) {
                showError("Invalid port number!");
            }
        });

        cancelButton.setOnAction(e -> primaryStage.setScene(welcomeScene));

        buttonBox.getChildren().addAll(saveButton, cancelButton);

        VBox.setMargin(grid, new Insets(10, 0, 20, 0));
        root.getChildren().addAll(
                titleLabel,
                embeddedCheckBox,
                infoLabel,
                separator,
                externalLabel,
                grid,
                buttonBox
        );

        return new Scene(root, 900, 600);
    }

    private Scene createJoinScene() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: #f5f5f5;");

        // Top section
        VBox topSection = new VBox(10);

        Label titleLabel = new Label("Available Chat Rooms");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        HBox statusBox = new HBox(10);
        statusBox.setAlignment(Pos.CENTER_LEFT);

        Label registryStatusLabel = new Label("Registry: " + registryHost + ":" + registryPort);
        registryStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

        Label lastRefreshLabel = new Label("Last refresh: Never");
        lastRefreshLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #999;");

        Button refreshButton = new Button("🔄 Refresh");
        ProgressIndicator refreshProgress = new ProgressIndicator();
        refreshProgress.setPrefSize(20, 20);
        refreshProgress.setVisible(false);

        refreshButton.setOnAction(e -> {
            refreshButton.setDisable(true);
            refreshProgress.setVisible(true);

            // Refresh în background
            new Thread(() -> {
                refreshRoomList();

                Platform.runLater(() -> {
                    refreshButton.setDisable(false);
                    refreshProgress.setVisible(false);
                    lastRefreshLabel.setText("Last refresh: " +
                            new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()));
                });
            }).start();
        });

        statusBox.getChildren().addAll(registryStatusLabel, lastRefreshLabel,
                refreshButton, refreshProgress);
        topSection.getChildren().addAll(titleLabel, statusBox);

        // Room list
        roomListView = new ListView<>();
        roomListView.setPlaceholder(new Label("No rooms available.\nClick Refresh to search for rooms."));
        roomListView.setCellFactory(lv -> new ListCell<ChatRoom>() {
            @Override
            protected void updateItem(ChatRoom room, boolean empty) {
                super.updateItem(room, empty);
                if (empty || room == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(room.toDetailedString());
                    setStyle("-fx-font-size: 13px; -fx-padding: 8;");
                }
            }
        });

        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(10));

        Button joinButton = new Button("Join Selected Room");
        joinButton.setDisable(true);
        joinButton.setStyle("-fx-font-size: 14px; -fx-padding: 8px 20px; " +
                "-fx-background-color: #2196F3; -fx-text-fill: white;");
        joinButton.setOnAction(e -> joinRoom());

        Button backButton = new Button("Back");
        backButton.setStyle("-fx-font-size: 14px; -fx-padding: 8px 20px;");
        backButton.setOnAction(e -> {
            stopRoomDiscovery();
            primaryStage.setScene(welcomeScene);
        });

        roomListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> joinButton.setDisable(newVal == null)
        );

        buttonBox.getChildren().addAll(joinButton, backButton);

        root.setTop(topSection);
        root.setCenter(roomListView);
        root.setBottom(buttonBox);

        return new Scene(root, 900, 600);
    }

    private Scene createChatScene() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        root.setStyle("-fx-background-color: #f5f5f5;");

        // Left panel - Client list
        VBox leftPanel = new VBox(10);
        leftPanel.setPrefWidth(200);
        leftPanel.setStyle("-fx-background-color: white; -fx-padding: 10; " +
                "-fx-background-radius: 5;");

        Label clientsLabel = new Label("Connected Users");
        clientsLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        clientCountLabel = new Label("0 users");
        clientCountLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

        clientListView = new ListView<>();
        clientListView.setPlaceholder(new Label("No users"));
        VBox.setVgrow(clientListView, Priority.ALWAYS);

        // Leave Room button
        Button leaveRoomButton = new Button("🚪 Leave Room");
        leaveRoomButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; " +
                "-fx-font-size: 12px; -fx-padding: 8px 15px; " +
                "-fx-background-radius: 5; -fx-cursor: hand;");
        leaveRoomButton.setMaxWidth(Double.MAX_VALUE);
        leaveRoomButton.setOnAction(e -> leaveRoom());

        leaveRoomButton.setOnMouseEntered(e ->
                leaveRoomButton.setStyle(leaveRoomButton.getStyle() + "-fx-opacity: 0.8;")
        );
        leaveRoomButton.setOnMouseExited(e ->
                leaveRoomButton.setStyle(leaveRoomButton.getStyle().replace("-fx-opacity: 0.8;", ""))
        );

        leftPanel.getChildren().addAll(clientsLabel, clientCountLabel, clientListView, leaveRoomButton);

        // Center panel - Chat
        VBox centerPanel = new VBox(10);

        Label chatLabel = new Label("Chat");
        chatLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 13px;");
        VBox.setVgrow(chatArea, Priority.ALWAYS);

        // Message input
        HBox inputBox = new HBox(10);
        messageField = new TextField();
        messageField.setPromptText("Type your message...");
        messageField.setStyle("-fx-font-size: 13px;");
        HBox.setHgrow(messageField, Priority.ALWAYS);

        sendButton = new Button("Send");
        sendButton.setStyle("-fx-font-size: 13px; -fx-padding: 8px 20px;");
        sendButton.setOnAction(e -> sendMessage());

        messageField.setOnAction(e -> sendMessage());

        inputBox.getChildren().addAll(messageField, sendButton);
        centerPanel.getChildren().addAll(chatLabel, chatArea, inputBox);

        // Status bar
        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-background-color: #e0e0e0; -fx-padding: 8; " +
                "-fx-font-size: 12px;");

        root.setLeft(leftPanel);
        root.setCenter(centerPanel);
        root.setBottom(statusLabel);
        BorderPane.setMargin(leftPanel, new Insets(0, 10, 0, 0));

        return new Scene(root, 900, 600);
    }

    private Button createStyledButton(String text, String color) {
        Button button = new Button(text);
        button.setStyle(String.format(
                "-fx-background-color: %s; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 14px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-padding: 12px 25px; " +
                        "-fx-background-radius: 5; " +
                        "-fx-cursor: hand;", color
        ));

        button.setOnMouseEntered(e ->
                button.setStyle(button.getStyle() + "-fx-opacity: 0.8;")
        );
        button.setOnMouseExited(e ->
                button.setStyle(button.getStyle().replace("-fx-opacity: 0.8;", ""))
        );

        return button;
    }

    private void setUserName(String name) {
        userName = name.trim().isEmpty() ? "User" : name.trim();
        primaryStage.setTitle("LAN Chat - " + userName);
    }

    private void initializeRegistry() {
        if (registryClient == null) {
            registryClient = new RoomRegistryClient(registryHost, registryPort);
        }
    }

    private void showHostDialog() {
        TextInputDialog dialog = new TextInputDialog("My Room");
        dialog.setTitle("Host Room");
        dialog.setHeaderText("Create a new chat room");
        dialog.setContentText("Room name:");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && !result.get().trim().isEmpty()) {
            String roomName = result.get().trim();
            hostRoom(roomName);
        }
    }

    private void hostRoom(String roomName) {
        try {
            // Găsește un port TCP liber
            assignedTcpPort = findAvailablePort();

            if (assignedTcpPort == -1) {
                showError("No available ports in range " + TCP_PORT_START + "-" + TCP_PORT_END +
                        "\nPlease close some applications and try again.");
                return;
            }

            System.out.println("✓ Assigned port: " + assignedTcpPort + " for room: " + roomName);

            // Pornește serverul de chat pe portul găsit
            chatServer = new ChatServer(assignedTcpPort, message ->
                    Platform.runLater(() -> {
                        // Nu afișăm mesajele speciale în chat
                        if (!message.startsWith("USERLIST:") &&
                                !message.startsWith("USER_JOINED:") &&
                                !message.startsWith("USER_LEFT:") &&
                                !message.startsWith("ROOM_CLOSED:")) {
                            appendMessage(message);
                        }
                    })
            );

            chatServer.setOnClientConnected(clientName ->
                    Platform.runLater(() -> {
                        updateClientList();
                        // Trimite lista actualizată de utilizatori către toți clienții
                        broadcastUserList();
                    })
            );

            chatServer.setOnClientDisconnected(clientName ->
                    Platform.runLater(() -> {
                        updateClientList();
                        // Trimite lista actualizată de utilizatori către toți clienții
                        broadcastUserList();
                    })
            );

            chatServer.start();

            // Obține IP-ul local
            String hostIP = InetAddress.getLocalHost().getHostAddress();

            // Înregistrează camera pe serverul de registry
            initializeRegistry();
            boolean registered = registryClient.registerRoom(roomName, userName, hostIP, assignedTcpPort);

            if (registered) {
                statusLabel.setText("Hosting: " + roomName + " | IP: " + hostIP + ":" + assignedTcpPort);
                appendMessage("=== You are hosting '" + roomName + "' ===");
                appendMessage("Room registered on registry server");
                appendMessage("Server listening on port: " + assignedTcpPort);
                appendMessage("Waiting for clients to join...");

                // Inițializează lista de utilizatori cu host-ul
                Platform.runLater(() -> {
                    clientListView.getItems().clear();
                    clientListView.getItems().add(userName + " (Host)");
                    clientCountLabel.setText("1 user");
                });

                primaryStage.setScene(chatScene);
            } else {
                showError("Failed to register room on registry server.\n" +
                        "Make sure the registry server is running.");
                if (chatServer != null) {
                    chatServer.stopServer();
                    chatServer = null;
                }
            }

        } catch (Exception e) {
            showError("Failed to host room: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Găsește un port TCP disponibil în range-ul definit.
     * @return portul disponibil găsit, sau -1 dacă niciun port nu e disponibil
     */
    private int findAvailablePort() {
        for (int port = TCP_PORT_START; port <= TCP_PORT_END; port++) {
            try (ServerSocket socket = new ServerSocket(port)) {
                socket.setReuseAddress(true);
                System.out.println("  Checking port " + port + " ... available ✓");
                return port;
            } catch (IOException e) {
                // Port ocupat, încearcă următorul
                System.out.println("  Checking port " + port + " ... in use");
            }
        }
        System.err.println("✗ No available ports in range " + TCP_PORT_START + "-" + TCP_PORT_END);
        return -1; // Niciun port disponibil
    }

    /**
     * Trimite lista de utilizatori către toți clienții conectați.
     */
    private void broadcastUserList() {
        if (chatServer == null) {
            System.out.println("[broadcastUserList] chatServer is null, skipping");
            return;
        }

        List<String> clients = chatServer.getConnectedClients();
        System.out.println("[broadcastUserList] Got clients from server: " + clients);

        // Construim lista: host + clienți
        StringBuilder userList = new StringBuilder();
        userList.append(userName).append(" (Host)");

        for (String client : clients) {
            userList.append(",").append(client);
        }

        // Trimitem lista către toți clienții (nu către host)
        String message = "USERLIST:" + userList.toString();
        System.out.println("[broadcastUserList] Broadcasting: " + message);
        chatServer.broadcastMessage(message);

        System.out.println("[broadcastUserList] Complete!");
    }

    private void startRoomDiscovery() {
        if (roomRefreshScheduler == null || roomRefreshScheduler.isShutdown()) {
            roomRefreshScheduler = Executors.newSingleThreadScheduledExecutor();
            roomRefreshScheduler.scheduleAtFixedRate(() -> {
                Platform.runLater(this::refreshRoomList);
            }, 0, 5, TimeUnit.SECONDS);
        }
    }

    private void stopRoomDiscovery() {
        if (roomRefreshScheduler != null && !roomRefreshScheduler.isShutdown()) {
            roomRefreshScheduler.shutdown();
        }
    }

    private void refreshRoomList() {
        // Rulează în background thread pentru a nu bloca UI
        new Thread(() -> {
            try {
                if (registryClient == null) {
                    initializeRegistry();
                }

                System.out.println("Refreshing room list...");
                List<ChatRoom> rooms = registryClient.listRooms();
                System.out.println("Retrieved " + rooms.size() + " room(s) from registry");

                // Actualizează UI în JavaFX thread
                Platform.runLater(() -> {
                    discoveredRooms.clear();
                    roomListView.getItems().clear();

                    if (rooms.isEmpty()) {
                        System.out.println("No rooms available");
                    } else {
                        for (ChatRoom room : rooms) {
                            String key = room.getUniqueKey();
                            discoveredRooms.put(key, room);
                            roomListView.getItems().add(room);
                            System.out.println("Added room: " + room.toDetailedString());
                        }
                    }
                });
            } catch (Exception e) {
                System.err.println("Error refreshing room list: " + e.getMessage());
                e.printStackTrace();

                Platform.runLater(() -> {
                    showError("Failed to refresh room list: " + e.getMessage());
                });
            }
        }).start();
    }

    private void joinRoom() {
        ChatRoom selectedRoom = roomListView.getSelectionModel().getSelectedItem();
        if (selectedRoom != null) {
            try {
                chatClient = new ChatClient(
                        selectedRoom.getHostIP(),
                        selectedRoom.getTcpPort(),
                        message -> Platform.runLater(() -> {
                            // IMPORTANT: Procesăm mesajele speciale ÎNAINTE de append
                            // Altfel ROOM_CLOSED e filtrat și nu ajunge la handler
                            updateClientListFromMessage(message);
                            appendMessage(message);
                        })
                );

                chatClient.setOnConnectionStatusChanged(connected ->
                        Platform.runLater(() -> {
                            if (!connected) {
                                statusLabel.setText("Disconnected from server");
                                showError("Lost connection to server");
                            }
                        })
                );

                chatClient.start();

                // Trimite mesaj de join
                chatClient.sendMessage("JOIN:" + userName);

                statusLabel.setText("Connected to: " + selectedRoom.getRoomName() +
                        " (Host: " + selectedRoom.getHostName() + ")");
                appendMessage("=== Connected to '" + selectedRoom.getRoomName() + "' ===");

                // Inițializăm lista cu host-ul
                Platform.runLater(() -> {
                    clientListView.getItems().clear();
                    clientListView.getItems().add(selectedRoom.getHostName() + " (Host)");
                    clientCountLabel.setText("1+ users");
                });

                // Mergi la scene-ul de chat
                stopRoomDiscovery();
                primaryStage.setScene(chatScene);

            } catch (IOException e) {
                showError("Failed to connect: " + e.getMessage());
            }
        }
    }

    /**
     * Actualizează lista de clienți din mesajele speciale primite.
     */
    private void updateClientListFromMessage(String message) {
        System.out.println("[updateClientListFromMessage] Processing: '" + message + "'");

        // Format: ROOM_CLOSED:reason
        if (message.startsWith("ROOM_CLOSED:")) {
            String reason = message.substring("ROOM_CLOSED:".length());
            System.out.println("[updateClientListFromMessage] ⚠️ ROOM CLOSED DETECTED!");
            System.out.println("[updateClientListFromMessage] Reason: " + reason);

            // Deconectare automată și notificare
            Platform.runLater(() -> {
                System.out.println("[updateClientListFromMessage] Running cleanup in UI thread...");

                // Cleanup imediat
                if (chatClient != null) {
                    try {
                        System.out.println("[updateClientListFromMessage] Disconnecting client...");
                        chatClient.disconnect();
                        chatClient = null;
                        System.out.println("[updateClientListFromMessage] ✓ Client disconnected");
                    } catch (Exception e) {
                        System.err.println("[updateClientListFromMessage] Error disconnecting: " + e.getMessage());
                    }
                }

                // Curăță UI
                System.out.println("[updateClientListFromMessage] Clearing UI...");
                chatArea.clear();
                messageField.clear();
                clientListView.getItems().clear();
                clientCountLabel.setText("0 users");
                statusLabel.setText("Disconnected");

                // Afișează dialog informativ
                System.out.println("[updateClientListFromMessage] Showing dialog to user...");
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Room Closed");
                alert.setHeaderText("The chat room has been closed");
                alert.setContentText(reason);
                alert.showAndWait();

                // Revino la welcome screen
                System.out.println("[updateClientListFromMessage] Returning to welcome screen...");
                primaryStage.setScene(welcomeScene);
                System.out.println("[updateClientListFromMessage] ✓✓✓ Returned to welcome screen after room closure");
            });
            return; // Nu procesăm mai departe
        }

        // Format: USERLIST:user1 (Host),user2,user3
        if (message.startsWith("USERLIST:")) {
            String userListStr = message.substring("USERLIST:".length());
            System.out.println("[updateClientListFromMessage] User list string: '" + userListStr + "'");

            if (userListStr.trim().isEmpty()) {
                System.out.println("[updateClientListFromMessage] User list is empty, returning");
                return;
            }

            String[] users = userListStr.split(",");
            System.out.println("[updateClientListFromMessage] Split into " + users.length + " users");

            Platform.runLater(() -> {
                clientListView.getItems().clear();
                System.out.println("[updateClientListFromMessage] Cleared client list view");

                for (int i = 0; i < users.length; i++) {
                    String user = users[i].trim();
                    System.out.println("[updateClientListFromMessage] Adding user[" + i + "]: '" + user + "'");
                    if (!user.isEmpty()) {
                        clientListView.getItems().add(user);
                    }
                }

                int count = clientListView.getItems().size();
                clientCountLabel.setText(count + " user" + (count != 1 ? "s" : ""));
                System.out.println("[updateClientListFromMessage] ✓ Final list: " + clientListView.getItems());
            });
        }
        // Format: USER_JOINED:username
        else if (message.startsWith("USER_JOINED:")) {
            String newUser = message.substring("USER_JOINED:".length()).trim();
            System.out.println("[updateClientListFromMessage] User joined: " + newUser);
            Platform.runLater(() -> {
                if (!clientListView.getItems().contains(newUser)) {
                    clientListView.getItems().add(newUser);
                    clientCountLabel.setText(clientListView.getItems().size() + " user" +
                            (clientListView.getItems().size() != 1 ? "s" : ""));
                    System.out.println("[updateClientListFromMessage] Added, now: " + clientListView.getItems());
                }
            });
        }
        // Format: USER_LEFT:username
        else if (message.startsWith("USER_LEFT:")) {
            String leftUser = message.substring("USER_LEFT:".length()).trim();
            System.out.println("[updateClientListFromMessage] User left: " + leftUser);
            Platform.runLater(() -> {
                clientListView.getItems().remove(leftUser);
                clientCountLabel.setText(clientListView.getItems().size() + " user" +
                        (clientListView.getItems().size() != 1 ? "s" : ""));
                System.out.println("[updateClientListFromMessage] Removed, now: " + clientListView.getItems());
            });
        }
    }

    /**
     * Părăsește camera de chat curentă și revine la welcome screen.
     */
    private void leaveRoom() {
        // Dialog de confirmare
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Leave Room");
        confirmDialog.setHeaderText("Are you sure you want to leave this room?");

        if (chatServer != null) {
            // Mesaj special pentru host
            confirmDialog.setContentText("You are the host. Leaving will close the room for all users.");
        } else {
            confirmDialog.setContentText("You will be disconnected from the chat.");
        }

        Optional<ButtonType> result = confirmDialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            System.out.println("[leaveRoom] User confirmed, leaving room...");

            // Cleanup pentru HOST
            if (chatServer != null) {
                System.out.println("[leaveRoom] Host is leaving, closing room for all clients...");

                // Păstrăm referință locală pentru thread
                final ChatServer serverToClose = chatServer;
                chatServer = null; // Setăm null acum pentru că nu mai e activ

                // Trimite mesaj special de închidere către TOȚI clienții
                // Acest mesaj va trigger automat deconectarea lor
                new Thread(() -> {
                    try {
                        System.out.println("[leaveRoom] Broadcasting ROOM_CLOSED...");
                        serverToClose.broadcastMessage("ROOM_CLOSED:The host has left the room");
                        System.out.println("[leaveRoom] ✓ Sent ROOM_CLOSED message to all clients");

                        // Așteaptă ca mesajul să ajungă la clienți
                        Thread.sleep(500);

                        // Acum oprește serverul
                        System.out.println("[leaveRoom] Stopping server...");
                        serverToClose.stopServer();
                        System.out.println("[leaveRoom] ✓ Chat server stopped");

                    } catch (Exception e) {
                        System.err.println("[leaveRoom] Error during host cleanup: " + e.getMessage());
                        e.printStackTrace();
                    }
                }).start();

                // Dezînregistrează camera din registry
                if (registryClient != null && assignedTcpPort > 0) {
                    try {
                        String hostIP = InetAddress.getLocalHost().getHostAddress();
                        registryClient.unregisterRoom(hostIP, assignedTcpPort);
                        System.out.println("[leaveRoom] Unregistered room from registry");
                    } catch (Exception e) {
                        System.err.println("[leaveRoom] Error unregistering room: " + e.getMessage());
                    }
                }
            }

            // Cleanup pentru CLIENT
            if (chatClient != null) {
                System.out.println("[leaveRoom] Client leaving room...");
                try {
                    // Trimite mesaj de plecare
                    chatClient.sendMessage(userName + " has left the chat");
                    Thread.sleep(100); // Scurt delay pentru mesaj

                    // Deconectare
                    chatClient.disconnect();
                    chatClient = null;
                    System.out.println("[leaveRoom] Client disconnected");
                } catch (Exception e) {
                    System.err.println("[leaveRoom] Error during client cleanup: " + e.getMessage());
                }
            }

            // Curăță UI - IMEDIAT, fără Thread.sleep care blochează UI
            Platform.runLater(() -> {
                chatArea.clear();
                messageField.clear();
                clientListView.getItems().clear();
                clientCountLabel.setText("0 users");
                statusLabel.setText("Ready");

                // Revino la welcome screen
                primaryStage.setScene(welcomeScene);
                System.out.println("[leaveRoom] ✓ Returned to welcome screen");
            });

        } else {
            System.out.println("[leaveRoom] User cancelled");
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
        // Nu afișăm mesajele speciale de protocol în chat
        if (message.startsWith("USERLIST:") ||
                message.startsWith("USER_JOINED:") ||
                message.startsWith("USER_LEFT:") ||
                message.startsWith("ROOM_CLOSED:")) {
            return;
        }
        chatArea.appendText(message + "\n");
    }

    private void updateClientList() {
        if (chatServer != null) {
            List<String> clients = chatServer.getConnectedClients();

            Platform.runLater(() -> {
                clientListView.getItems().clear();
                clientListView.getItems().add(userName + " (Host)");

                for (String client : clients) {
                    if (!client.equals(userName)) { // Nu adăugăm host-ul de 2 ori
                        clientListView.getItems().add(client);
                    }
                }

                int totalUsers = clients.size() + 1; // +1 pentru host
                clientCountLabel.setText(totalUsers + " user" + (totalUsers != 1 ? "s" : ""));

                System.out.println("Host updated client list: " + totalUsers + " users total");
            });
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void cleanup() {
        System.out.println("Cleaning up resources...");

        // Oprim discovery-ul
        stopRoomDiscovery();

        // Cleanup registry client
        if (registryClient != null) {
            try {
                registryClient.shutdown();
                System.out.println("✓ Registry client shutdown");
            } catch (Exception e) {
                System.err.println("⚠ Error shutting down registry client: " + e.getMessage());
            }
            registryClient = null;
        }

        // Cleanup chat server
        if (chatServer != null) {
            try {
                chatServer.stopServer();
                // Așteptăm puțin ca serverul să se oprească
                Thread.sleep(200);
                System.out.println("✓ Chat server stopped");
            } catch (Exception e) {
                System.err.println("⚠ Error stopping chat server: " + e.getMessage());
            }
            chatServer = null;
        }

        // Cleanup chat client
        if (chatClient != null) {
            try {
                chatClient.disconnect();
                System.out.println("✓ Chat client disconnected");
            } catch (Exception e) {
                System.err.println("⚠ Error disconnecting chat client: " + e.getMessage());
            }
            chatClient = null;
        }

        // Cleanup embedded registry server - ultimul pas
        if (embeddedRegistryServer != null && useEmbeddedRegistry) {
            try {
                embeddedRegistryServer.shutdown();
                // Așteptăm ca serverul să se oprească
                Thread.sleep(200);
                System.out.println("✓ Embedded registry server stopped");
            } catch (Exception e) {
                System.err.println("⚠ Error stopping registry server: " + e.getMessage());
            }
            embeddedRegistryServer = null;
        }

        System.out.println("✓ Cleanup complete!");
    }

    public static void main(String[] args) {
        launch(args);
    }
}