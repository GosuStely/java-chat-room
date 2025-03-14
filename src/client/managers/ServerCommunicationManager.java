package client.managers;

import Utilities.messages.privateMessage.PrivateMsg;
import Utilities.messages.privateMessage.PrivateMsgResp;
import Utilities.messages.requestList.ListResp;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import Utilities.Commands;
import Utilities.messages.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

public class ServerCommunicationManager {
    private BufferedReader serverReader;
    private PrintWriter writer;
    private ObjectMapper mapper;

    public ServerCommunicationManager(BufferedReader serverReader, PrintWriter writer, ObjectMapper mapper) {
        this.serverReader = serverReader;
        this.writer = writer;
        this.mapper = mapper;
    }

    public void sendCommand(String command, Object message) throws JsonProcessingException {
        String jsonMessage = command + " " + (message == null ? "{}" : mapper.writeValueAsString(message));
        writer.println(jsonMessage);
    }

    public void respondToPing() throws JsonProcessingException {
        sendCommand(Commands.PONG, new Pong());
    }

    public void processBroadcastResponse(String jsonPayload) throws JsonProcessingException {
        BroadcastResp broadcastResp = mapper.readValue(jsonPayload, BroadcastResp.class);
        if (broadcastResp.status().equals("OK")) {
            System.out.println("Sent");
        } else {
            displayBroadcastError(broadcastResp.code());
        }
    }

    private void displayBroadcastError(int errorCode) {
        if (errorCode == 6000) {
            System.out.println("Error: You must log in before sending a broadcast message.");
        } else {
            System.out.println("Unknown broadcast error occurred. Code: " + errorCode);
        }
    }

    public void showBroadcastMessage(String jsonPayload) throws JsonProcessingException {
        Broadcast broadcast = mapper.readValue(jsonPayload, Broadcast.class);
        System.out.println(broadcast.username() + ": " + broadcast.message());
    }

    public void handleConnectionTermination() {
        System.out.println("Received HANGUP due to missing PONG");
    }

    public void displayConnectedClients(String jsonPayload) throws JsonProcessingException {
        ListResp listResp = mapper.readValue(jsonPayload, ListResp.class);

        if ("ERROR".equals(listResp.status())) {
            if (listResp.code() == 9000) {
                System.out.println("Cannot retrieve list: You are not logged in.");
            } else {
                System.out.println("Unknown error retrieving list: " + listResp.code());
            }
        } else {
            if (listResp.clients() != null && !listResp.clients().isEmpty()) {
                String clientListString = String.join(", ", listResp.clients());
                System.out.println("Currently connected users: " + clientListString);
            } else {
                System.out.println("(no users connected?)");
            }
        }
    }

    public void notifyUserJoined(String jsonPayload) throws JsonProcessingException {
        Joined joined = mapper.readValue(jsonPayload, Joined.class);
        System.out.println(joined.username() + " has joined the chat.");
    }

    public void notifyUserLeft(String jsonPayload) throws JsonProcessingException {
        Joined left = mapper.readValue(jsonPayload, Joined.class);
        System.out.println(left.username() + " has left the chat.");
    }

    public void displayPrivateMessage(String jsonPayload) throws JsonProcessingException {
        PrivateMsg privateMsg = mapper.readValue(jsonPayload, PrivateMsg.class);
        System.out.println("[PRIVATE] " + privateMsg.sender() + ": " + privateMsg.message());
    }

    public void handlePrivateMessageError(String jsonPayload) throws JsonProcessingException {
        PrivateMsgResp privateMsgResp = mapper.readValue(jsonPayload, PrivateMsgResp.class);
        if (privateMsgResp.status().equals("ERROR")) {
            switch (privateMsgResp.code()) {
                case 10001 -> System.out.println("Please log in to send private message.");
                case 10002 -> System.out.println("No receiver found.");
                case 10003 -> System.out.println("Can't send to self.");
            }
        } else {
            System.out.println("Sent");
        }
    }
}