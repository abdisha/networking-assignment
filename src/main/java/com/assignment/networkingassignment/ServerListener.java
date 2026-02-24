package com.assignment.networkingassignment;

public interface ServerListener {
    void onUserConnect(String ip);
    void onUserDisconnect(String ip);
}
