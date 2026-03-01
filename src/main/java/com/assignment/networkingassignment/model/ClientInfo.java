package com.assignment.networkingassignment.model;

import java.io.PrintWriter;

public record ClientInfo(String userName, String ip, int port, PrintWriter writer) {
    public String getFullAddress(){
        return ip+":"+port;
    }

}
