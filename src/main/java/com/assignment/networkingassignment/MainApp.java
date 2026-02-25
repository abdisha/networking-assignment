package com.assignment.networkingassignment;

import com.assignment.networkingassignment.networking.TcpEngine;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.DatagramSocket;
import java.net.InetAddress;


public class MainApp extends Application implements ServerListener {
    private final ObservableList<String> userList = FXCollections.observableArrayList();
    private TcpEngine server;

    @Override
    public void start(Stage primaryStage) {
        ListView<String> listView = new ListView<>(userList);
        TextField adminInput = new TextField();
        Button broadcastBtn = new Button("Broadcast");

        String myIp = getServerIP();
        Label ipLabel = new Label("Server IP: " + myIp + " | Port: 65432");
        ipLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: blue;");

        broadcastBtn.setOnAction(e -> {
            server.broadcast("ADMIN: " + adminInput.getText());
            adminInput.clear();
        });

        VBox root = new VBox(10,ipLabel, new Label("Online Users:"), listView, new HBox(5, adminInput, broadcastBtn));
        root.setPadding(new Insets(15));

        server = new TcpEngine(65432, this);
        server.start();

        primaryStage.setTitle("TCP Server Monitor");
        primaryStage.setScene(new Scene(root, 400, 500));
        primaryStage.show();
    }



    public String getServerIP() {
        try (final DatagramSocket socket = new DatagramSocket()) {
            // We "connect" to a dummy address to force the OS to pick
            // the correct network interface used for internet/LAN.
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            return socket.getLocalAddress().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1"; // Fallback
        }
    }

    @Override public void onUserConnect(String ip) { Platform.runLater(() -> userList.add(ip)); }
    @Override public void onUserDisconnect(String ip) { Platform.runLater(() -> userList.remove(ip)); }
    @Override public void stop() { if (server != null) server.stop(); }
}
