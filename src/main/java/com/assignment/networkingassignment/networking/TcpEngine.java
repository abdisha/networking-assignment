package com.assignment.networkingassignment.networking;

import com.assignment.networkingassignment.ServerListener;
import java.io.*;
import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class TcpEngine {

    private final int port;
    private final ServerListener listener;
    private final Map<String, PrintWriter> clientMap = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(10, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true); // Ensures threads don't block JVM shutdown
        return t;
    });

    private ServerSocket serverSocket;
    private volatile boolean running = true;

    public TcpEngine(int port, ServerListener listener) {
        this.port = port;
        this.listener = listener;
    }

    public void start() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                System.out.println("Server started on port " + port);
                while (running) {
                    Socket client = serverSocket.accept();
                    // Clean the IP string to remove the leading "/"
                    String ipIdentifier = client.getRemoteSocketAddress().toString().substring(1);
                    pool.execute(() -> handleClient(client, ipIdentifier));
                }
            } catch (IOException e) {
                if (running) System.err.println("Server Stopped: " + e.getMessage());
            }
        }).start();
    }

    private void handleClient(Socket socket, String ipIdentifier) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            clientMap.put(ipIdentifier, out);
            listener.onUserConnect(ipIdentifier);
            broadcastUserList();

            String message;
            while (running && (message = in.readLine()) != null) {
                processIncomingData(ipIdentifier, message);
            }

        } catch (IOException e) {
            // Socket closed or user disconnected
        } finally {
            cleanupClient(ipIdentifier, socket);
        }
    }

    private void processIncomingData(String senderIp, String message) {
        // Logs for debugging
        System.out.println("Received from [" + senderIp + "]: " + message);

        if (message.startsWith("PRIVATE_MSG:")) {
            handlePrivateMessage(senderIp, message);
        } else if (message.startsWith("VIDEO_INVITE:")) {
            handleVideoInvite(senderIp, message);
        } else if (message.startsWith("VIDEO_RESPONSE:")) {
            handleVideoResponse(senderIp, message);
        } else if (message.startsWith("VIDEO_HANGUP:")) {
            handleVideoHangup(senderIp, message);
        } else {
            broadcast("[" + senderIp + "]: " + message);
        }
    }

    private void handlePrivateMessage(String senderIp, String rawMessage) {
        // Format: PRIVATE_MSG:TargetIP:Port:ActualMessage
        String[] parts = rawMessage.split(":", 4);
        if (parts.length >= 4) {
            String targetIp = parts[1] + ":" + parts[2];
            String content = parts[3];

            sendMessageTo(targetIp, "[" + getTimestamp() + "] [Whisper from " + senderIp + "]: " + content);
            sendMessageTo(senderIp, "[" + getTimestamp() + "] [Whisper to " + targetIp + "]: " + content);
        }
    }

    private void handleVideoInvite(String senderIp, String rawMessage) {
        // Format: VIDEO_INVITE:TargetIP:Port
        String targetIp = extractTargetIp(rawMessage);
        sendMessageTo(targetIp, "VIDEO_PROMPT:" + senderIp);
    }

    private void handleVideoResponse(String responderIp, String rawMessage) {
        // Format: VIDEO_RESPONSE:TargetIP:Port:ACCEPT_OR_REJECT
        String[] parts = rawMessage.split(":");
        if (parts.length >= 4) {
            String targetIp = parts[1] + ":" + parts[2];
            String status = parts[3];
            sendMessageTo(targetIp, "VIDEO_RESULT:" + responderIp + ":" + status);
        }
    }

    private void handleVideoHangup(String senderIp, String rawMessage) {
        String targetIp = extractTargetIp(rawMessage);
        sendMessageTo(targetIp, "VIDEO_TERMINATED");
    }

    private void sendMessageTo(String targetIp, String message) {
        PrintWriter writer = clientMap.get(targetIp);
        if (writer != null) {
            writer.println(message);
        }
    }

    private String extractTargetIp(String rawMessage) {
        // Helper to handle the "Prefix:IP:Port" structure
        String[] parts = rawMessage.split(":");
        if (parts.length >= 3) {
            return parts[1] + ":" + parts[2];
        }
        return "";
    }

    private void broadcastUserList() {
        String list = "USER_LIST:" + String.join(",", clientMap.keySet());
        clientMap.values().forEach(writer -> writer.println(list));
    }

    public void broadcast(String message) {
        String formatted = "[" + getTimestamp() + "] " + message;
        clientMap.values().forEach(writer -> writer.println(formatted));
    }

    private String getTimestamp() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm:ss a"));
    }

    private void cleanupClient(String ipIdentifier, Socket socket) {
        clientMap.remove(ipIdentifier);
        listener.onUserDisconnect(ipIdentifier);
        broadcastUserList();
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
            pool.shutdownNow();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}