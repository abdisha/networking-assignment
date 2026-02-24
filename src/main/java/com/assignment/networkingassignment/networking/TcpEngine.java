package com.assignment.networkingassignment.networking;

import com.assignment.networkingassignment.ServerListener;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class TcpEngine {

    private final int port;
    private final ServerListener listener;
    private final Map<String, PrintWriter> clientMap = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(10, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
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
                while (running) {
                    Socket client = serverSocket.accept();
                    String ip = client.getRemoteSocketAddress().toString();
                    pool.execute(() -> handleClient(client, ip));
                }
            } catch (IOException e) {
                if (running) System.err.println("Server Stopped: " + e.getMessage());
            }
        }).start();
    }

    private void handleClient(Socket socket, String ip) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            clientMap.put(ip, out);
            listener.onUserConnect(ip);
            broadcastUserList();

            String message;
            while (running && (message = in.readLine()) != null) {
                System.out.println(message);
                if (message.startsWith("PRIVATE_MSG:")) {
                    handlePrivateMessage(ip, message);
                } else if (message.startsWith("VIDEO_INVITE:")) {
                    // Format: VIDEO_INVITE:TargetIP
                    handleVideoInvite(ip, message);
                } else if (message.startsWith("VIDEO_RESPONSE:")) {
                    // Format: VIDEO_RESPONSE:TargetIP:ACCEPT or REJECT
                    handleVideoResponse(ip, message);
                } else {
                    broadcast("[" + ip + "]: " + message);
                }
            }
        } catch (IOException e) { /* Disconnect handled in finally */ } finally {
            clientMap.remove(ip);
            listener.onUserDisconnect(ip);
            broadcastUserList();
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handlePrivateMessage(String senderIp, String rawMessage) {
        String[] parts = rawMessage.split(":", 4);
        if (parts.length == 4) {
            String targetIp = parts[1] + ":" + parts[2];
            String msgBody = parts[3];
            System.out.println("targetIp: " + targetIp + " msgBody: " + msgBody);
            String time = getTimestamp();
            PrintWriter target = clientMap.get(targetIp);
            if (target != null) {
                target.println("[" + time + "] [Whisper from " + senderIp + "]: " + msgBody);
                clientMap.get(senderIp).println("[" + time + "] [Whisper to " + targetIp + "]: " + msgBody);
            }
        }
    }

    private void handleVideoInvite(String senderIp, String rawMessage) {
        String targetIp = rawMessage.split(":")[1];
        PrintWriter targetWriter = clientMap.get(targetIp);
        if (targetWriter != null) {
            // Tell the target that sender wants to call
            targetWriter.println("VIDEO_PROMPT:" + senderIp);
        }
    }

    private void handleVideoResponse(String responderIp, String rawMessage) {
        String[] parts = rawMessage.split(":");
        String targetIp = parts[1];
        String status = parts[2]; // "ACCEPT" or "REJECT"

        PrintWriter targetWriter = clientMap.get(targetIp);
        if (targetWriter != null) {
            targetWriter.println("VIDEO_RESULT:" + responderIp + ":" + status);
        }
    }
    private void broadcastUserList() {
        String list = "USER_LIST:" + String.join(",", clientMap.keySet());
        for (PrintWriter writer : clientMap.values()) {
            writer.println(list);
        }
    }

    public void broadcast(String message) {
        String formatted = "[" + getTimestamp() + "] " + message;
        clientMap.values().forEach(writer -> writer.println(formatted));
    }

    private String getTimestamp() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm:ss a"));
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}