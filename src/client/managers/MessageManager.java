package client.managers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import Utilities.Commands;
import Utilities.messages.*;
import Utilities.messages.fileTransfer.FileTransferReady;
import Utilities.messages.fileTransfer.FileTransferReq;
import Utilities.messages.fileTransfer.FileTransferResp;
import Utilities.messages.privateMessage.PrivateMsg;
import Utilities.messages.privateMessage.PrivateMsgReq;
import Utilities.messages.privateMessage.PrivateMsgResp;
import Utilities.messages.requestList.ListReq;
import Utilities.messages.requestList.ListResp;
import Utilities.messages.rockPaperScissor.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.function.Consumer;

public class MessageManager {
    private final ObjectMapper mapper;
    private final PrintWriter writer;
    private final Consumer<FileTransferReady> fileTransferReadyHandler;
    private final Consumer<FileTransferReq> incomingFileRequestHandler;
    private final String username;

    public MessageManager(PrintWriter writer, ObjectMapper mapper, String username,
                          Consumer<FileTransferReady> fileTransferReadyHandler,
                          Consumer<FileTransferReq> incomingFileRequestHandler) {
        this.writer = writer;
        this.mapper = mapper;
        this.username = username;
        this.fileTransferReadyHandler = fileTransferReadyHandler;
        this.incomingFileRequestHandler = incomingFileRequestHandler;
    }

    /**
     * Processes a single message received from the server.
     * Dispatches the message to the appropriate handler based on its command type.
     *
     * @param serverMessage The message received from the server.
     * @throws IOException If an error occurs during message parsing or handling.
     */
    public void handleServerMessage(String serverMessage) throws IOException {
        String[] parts = serverMessage.split(" ", 2);
        String command = parts[0];
        String jsonPayload = parts.length > 1 ? parts[1] : "{}";

        switch (command) {
            case Commands.PING -> respondToPing();
            case Commands.HANGUP -> System.out.println("Received HANGUP due to missing PONG");
            case Commands.BROADCAST_RESP -> processBroadcastResponse(jsonPayload);
            case Commands.BROADCAST -> showBroadcastMessage(jsonPayload);
            case Commands.JOINED -> notifyUserJoined(jsonPayload);
            case Commands.LEFT -> notifyUserLeft(jsonPayload);
            case Commands.BYE_RESP -> System.out.println("Goodbye!");
            case Commands.LIST_RESP -> displayConnectedClients(jsonPayload);
            case Commands.PRIVATE_MSG -> displayPrivateMessage(jsonPayload);
            case Commands.PRIVATE_MSG_RESP -> handlePrivateMessageResponse(jsonPayload);
            case Commands.RPS_START_RESP -> processRpsGameInvitationResponse(jsonPayload);
            case Commands.RPS_INVITE -> processRpsGameInvitation(jsonPayload);
            case Commands.RPS_INVITE_DECLINED -> System.out.println("Game invitation declined.");
            case Commands.RPS_READY -> System.out.println("Please select your move: /r, /p, /s");
            case Commands.RPS_MOVE_RESP -> processRpsMoveResponse(jsonPayload);
            case Commands.RPS_RESULT -> displayRpsGameResult(jsonPayload);
            case Commands.FILE_TRANSFER_REQ -> processIncomingFileRequest(jsonPayload);
            case Commands.FILE_TRANSFER_RESP -> processFileTransferResponse(jsonPayload);
            case Commands.FILE_TRANSFER_READY -> processFileTransferReady(jsonPayload);
            default -> System.out.println("Unknown server message: " + serverMessage);
        }
    }

    /**
     * Sends a command and its associated message to the server.
     *
     * @param command The command type.
     * @param message The message object to be sent (can be `null`).
     * @throws JsonProcessingException If the message cannot be serialized.
     */
    public void sendServerCommand(String command, Object message) throws JsonProcessingException {
        String jsonMessage = command + " " + (message == null ? "{}" : mapper.writeValueAsString(message));
        writer.println(jsonMessage);
    }

    private void respondToPing() throws JsonProcessingException {
        sendServerCommand(Commands.PONG, new Pong());
    }

    private void processBroadcastResponse(String jsonPayload) throws JsonProcessingException {
        BroadcastResp broadcastResp = mapper.readValue(jsonPayload, BroadcastResp.class);
        if ((broadcastResp.status()).equals("OK")) {
            System.out.println("Sent ✔");
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

    private void showBroadcastMessage(String jsonPayload) throws JsonProcessingException {
        Broadcast broadcast = mapper.readValue(jsonPayload, Broadcast.class);
        System.out.println("[PUBLIC] " + broadcast.username() + ": " + broadcast.message());
    }

    private void displayConnectedClients(String jsonPayload) throws JsonProcessingException {
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

    private void notifyUserJoined(String jsonPayload) throws JsonProcessingException {
        Joined joined = mapper.readValue(jsonPayload, Joined.class);
        System.out.println(joined.username() + " has joined the chat.");
    }

    private void notifyUserLeft(String jsonPayload) throws JsonProcessingException {
        Joined left = mapper.readValue(jsonPayload, Joined.class);
        System.out.println(left.username() + " has left the chat.");
    }

    private void displayPrivateMessage(String jsonPayload) throws JsonProcessingException {
        PrivateMsg privateMsg = mapper.readValue(jsonPayload, PrivateMsg.class);
        System.out.println("[PRIVATE] " + privateMsg.sender() + ": " + privateMsg.message());
    }

    private void handlePrivateMessageResponse(String jsonPayload) throws JsonProcessingException {
        PrivateMsgResp privateMsgResp = mapper.readValue(jsonPayload, PrivateMsgResp.class);
        if (privateMsgResp.status().equals("ERROR")) {
            switch (privateMsgResp.code()) {
                case 10001 -> System.out.println("Please log in to send private message.");
                case 10002 -> System.out.println("No receiver found.");
                case 10003 -> System.out.println("Can't send to self.");
            }
        } else {
            System.out.println("Sent ✔");
        }
    }

    private void processRpsGameInvitationResponse(String jsonPayload) throws JsonProcessingException {
        RpsStartResp rpsStartResp = mapper.readValue(jsonPayload, RpsStartResp.class);
        if (rpsStartResp.status().equals("ERROR")) {
            switch (rpsStartResp.code()) {
                case 11001 -> System.out.println("You need to log in first. Please try again");
                case 11002 -> System.out.println("No opponent found");
                case 11003 -> System.out.println("Can't send game request to self");
                case 11004 ->
                        System.out.println("A game is ongoing between " + rpsStartResp.player1() + " and " + rpsStartResp.player2());
            }
        } else {
            System.out.println("Invitation sent ✔");
        }
    }

    private void processRpsGameInvitation(String jsonPayload) throws IOException {
        RpsInvite rpsInvite = mapper.readValue(jsonPayload, RpsInvite.class);

        System.out.println("You have been invited to a game by " + rpsInvite.sender());
        System.out.println("Would you like to accept?");
        System.out.println("/y - yes");
        System.out.println("/n - no");
    }

    private void processRpsMoveResponse(String jsonPayload) throws JsonProcessingException {
        RpsMoveResp rpsMoveResp = mapper.readValue(jsonPayload, RpsMoveResp.class);
        if (rpsMoveResp.status().equals("OK")) {
            System.out.println("Move sent ✔");
        } else if (rpsMoveResp.status().equals("ERROR") && rpsMoveResp.code() == 11005) {
            System.out.println("No ongoing game.");
        } else {
            System.out.println("Unknown Move response from Server");
        }
    }

    private void displayRpsGameResult(String jsonPayload) throws JsonProcessingException {
        Map<String, Object> result = mapper.readValue(jsonPayload, Map.class);
        String winner = (String) result.get("winner");

        if (winner == null) {
            System.out.println("It's a tie!");
        } else {
            System.out.println("The winner is: " + winner);
        }
    }

    private void processFileTransferResponse(String jsonPayload) throws JsonProcessingException {
        FileTransferResp fileTransferResp = mapper.readValue(jsonPayload, FileTransferResp.class);
        if (fileTransferResp.status().equals("OK")) {
            System.out.println("File transfer request sent ✔");
        } else if (fileTransferResp.status().equals("DECLINE")) {
            System.out.println("File request declined.");
        } else {
            switch (fileTransferResp.code()) {
                case 13000 -> System.out.println("Please log in first.");
                case 13001 -> System.out.println("No receiver found.");
                case 13002 -> System.out.println("Can't send the file to yourself.");
            }
        }
    }

    private void processIncomingFileRequest(String jsonPayload) throws IOException {
        FileTransferReq req = mapper.readValue(jsonPayload, FileTransferReq.class);
        incomingFileRequestHandler.accept(req);
        System.out.println("New file transfer request from: " + req.sender());
    }

    private void processFileTransferReady(String jsonPayload) throws JsonProcessingException {
        FileTransferReady fileTransferReady = mapper.readValue(jsonPayload, FileTransferReady.class);
        fileTransferReadyHandler.accept(fileTransferReady);
    }

    public void sendPrivateMessage(String receiver, String message) throws JsonProcessingException {
        sendServerCommand(Commands.PRIVATE_MSG_REQ, new PrivateMsgReq(receiver, message));
    }

    public void sendBroadcastMessage(String message) throws JsonProcessingException {
        sendServerCommand(Commands.BROADCAST_REQ, new BroadcastReq(message));
    }

    public void requestClientList() throws JsonProcessingException {
        sendServerCommand(Commands.LIST_REQ, new ListReq());
    }

    public void sendRpsStartRequest(String opponent) throws JsonProcessingException {
        sendServerCommand(Commands.RPS_START_REQ, new RpsStartReq(opponent));
    }

    public void respondToRpsInvitation(boolean accept) throws JsonProcessingException {
        if (accept) {
            sendServerCommand(Commands.RPS_INVITE_RESP, new RpsInviteResp("ACCEPT"));
            System.out.println("Invitation accepted");
        } else {
            sendServerCommand(Commands.RPS_INVITE_RESP, new RpsInviteResp("DECLINE"));
            System.out.println("Invitation declined");
        }
    }

    public void sendRpsMove(String move) throws JsonProcessingException {
        sendServerCommand(Commands.RPS_MOVE_REQ, new RpsMove(move));
    }

    public void sendFileTransferRequest(String receiver, String filename, String checksum) throws JsonProcessingException {
        FileTransferReq fileTransferReq = new FileTransferReq(username, receiver, filename, checksum);
        sendServerCommand(Commands.FILE_TRANSFER_REQ, fileTransferReq);
    }

    public void sendFileTransferResponse(boolean accept) throws JsonProcessingException {
        if (accept) {
            System.out.println("Accepted file transfer");
            sendServerCommand(Commands.FILE_TRANSFER_RESP, new FileTransferResp("ACCEPT", 0));
        } else {
            System.out.println("Declined file transfer");
            sendServerCommand(Commands.FILE_TRANSFER_RESP, new FileTransferResp("DECLINE", 0));
        }
    }

    public void sendBye() throws JsonProcessingException {
        sendServerCommand(Commands.BYE, new Bye());
    }
}