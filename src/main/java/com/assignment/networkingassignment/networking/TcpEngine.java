package com.assignment.networkingassignment.networking;

import com.assignment.networkingassignment.ServerListener;
import com.assignment.networkingassignment.model.ClientInfo;

import java.io.*;
import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class TcpEngine {

    private final int port;
    private final ServerListener listener;
    private final Map<String, ClientInfo> clientMap = new ConcurrentHashMap<>();
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

            String message;
            while (running && (message = in.readLine()) != null) {
                processIncomingData(ipIdentifier,out, message);
            }

        } catch (IOException e) {
            // Socket closed or user disconnected
        } finally {
            cleanupClient(ipIdentifier, socket);
        }
    }

    private ClientInfo getClientInfo(String ipIdentifier, PrintWriter out, String username) {
        return new ClientInfo(
                username,
                ipIdentifier.split(":")[0],
                Integer.parseInt(ipIdentifier.split(":")[1]),
                out
        );
    }

    private void processIncomingData(String senderIp, PrintWriter out, String message) {
        // Logs for debugging
        System.out.println("sender ["+senderIp+"], message["+ message+"]");
        if (message.startsWith("LOGIN:")) {
            ClientInfo clientInfo = getClientInfo(senderIp,out, message.split(":")[1]);
            clientMap.put(senderIp, clientInfo);
            listener.onUserConnect(senderIp);
            broadcastUserList();
        }else if (message.startsWith("PRIVATE_MSG:")) {
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
        String targetIp = extractIpAddress(rawMessage);
        sendMessageTo(targetIp, "VIDEO_PROMPT:" + senderIp);
    }

    private String extractIpAddress(String rawMessage) {
        String userName= rawMessage.split(":")[1];
        return clientMap.values().stream()
                .filter(clientInfo -> clientInfo.userName().equals(userName))
                .findFirst().get().ip();
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
        String targetIp = extractIpAddress(rawMessage);
        sendMessageTo(targetIp, "VIDEO_TERMINATED");
    }

    private void sendMessageTo(String targetIp, String message) {
         ClientInfo clientInfo = clientMap.get(targetIp);
        if (clientInfo != null) {
            clientInfo.writer().println(message);
        }
    }



    private void broadcastUserList() {
        String list = "USER_LIST:" + String.join(",", clientMap.values().stream().map(ClientInfo::userName).toList());
        clientMap.values().forEach(clientInfo -> clientInfo.writer().println(list));
    }

    public void broadcast(String message) {
        String formatted = "[" + getTimestamp() + "] " + message;
        clientMap.values().forEach(clientInfo -> clientInfo.writer().println(formatted));
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