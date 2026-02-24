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


public class MainApp extends Application implements ServerListener {
    private final ObservableList<String> userList = FXCollections.observableArrayList();
    private TcpEngine server;

    @Override
    public void start(Stage primaryStage) {
        ListView<String> listView = new ListView<>(userList);
        TextField adminInput = new TextField();
        Button broadcastBtn = new Button("Broadcast");

        broadcastBtn.setOnAction(e -> {
            server.broadcast("ADMIN: " + adminInput.getText());
            adminInput.clear();
        });

        VBox root = new VBox(10, new Label("Online Users:"), listView, new HBox(5, adminInput, broadcastBtn));
        root.setPadding(new Insets(15));

        server = new TcpEngine(65432, this);
        server.start();

        primaryStage.setTitle("TCP Server Monitor");
        primaryStage.setScene(new Scene(root, 400, 500));
        primaryStage.show();
    }

    @Override public void onUserConnect(String ip) { Platform.runLater(() -> userList.add(ip)); }
    @Override public void onUserDisconnect(String ip) { Platform.runLater(() -> userList.remove(ip)); }
    @Override public void stop() { if (server != null) server.stop(); }
}
