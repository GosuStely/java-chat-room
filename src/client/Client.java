package client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import Utilities.Commands;
import Utilities.Utils;
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
import client.managers.FileTransferManager;
import client.managers.MessageManager;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Client {
    private static final String SERVER_ADDRESS = "127.0.0.1";

    private static Socket socket;
    private static BufferedReader userReader;
    private static BufferedReader serverReader;
    private static ObjectMapper mapper;
    private static PrintWriter writer;

    private static String username;
    private static FileTransferManager fileTransferManager;
    private static MessageManager messageManager;

    public static void main(String[] args) {
        try {
            socket = new Socket(SERVER_ADDRESS, Utils.SERVER_PORT);

            mapper = new ObjectMapper();

            if (!establishServerConnection()) return;

            userReader = new BufferedReader(new InputStreamReader(System.in));
            writer = new PrintWriter(socket.getOutputStream(), true);

            username = authenticateUser();
            if (username == null) return;

            // Initialize managers
            fileTransferManager = new FileTransferManager(SERVER_ADDRESS);
            messageManager = new MessageManager(writer, mapper, username, fileTransferManager::handleFileTransfer, fileTransferManager::addIncomingRequest);

            enterChatSession();
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static boolean establishServerConnection() throws IOException {
        serverReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        String serverResponse = serverReader.readLine();

        if (serverResponse == null) {
            System.out.println("Failed to connect: No response from server.");
            return false;
        }

        try {
            String[] parts = serverResponse.split(" ", 2);
            if (parts.length < 2 || !Commands.READY.equals(parts[0])) {
                System.out.println("Unexpected response from server.");
                return false;
            }

            Ready readyMessage = mapper.readValue(parts[1], Ready.class);
            System.out.println("Server connected successfully! Version: " + readyMessage.version());
            return true;
        } catch (JsonProcessingException e) {
            System.out.println("Failed to parse server response: " + e.getMessage());
            return false;
        }
    }

    private static String authenticateUser() throws IOException {
        while (true) {
            System.out.print("Enter username: ");
            String usernameInput = userReader.readLine();

            Enter enterMessage = new Enter(usernameInput);
            sendServerCommand(Commands.ENTER, enterMessage);

            String serverResponse = serverReader.readLine();
            if (serverResponse == null) {
                System.out.println("No response from server. Exiting...");
                return null;
            }

            String[] parts = serverResponse.split(" ", 2);
            if (parts.length < 2 || !Commands.ENTER_RESP.equals(parts[0])) {
                System.out.println("Unexpected response from server.");
                continue;
            }

            EnterResp enterResp = mapper.readValue(parts[1], EnterResp.class);
            if (enterResp.status().equals("OK")) {
                System.out.println("Logged in as " + usernameInput);
                username = usernameInput;
                return usernameInput;
            }

            displayLoginError(enterResp.code());
        }
    }

    private static void displayLoginError(int errorCode) {
        switch (errorCode) {
            case 5000 -> System.out.println("User with this name already exists.");
            case 5001 ->
                    System.out.println("A username may only consist of 3-14 characters, numbers, and underscores.");
            case 5002 -> System.out.println("User is already logged in.");
            default -> System.out.println("Unknown error occurred.");
        }
    }

    private static void enterChatSession() throws IOException {
        System.out.println("You are now in chat mode.");
        showHelpMenu();
        Thread listenerThread = setupServerMessageListener();
        listenerThread.start();
        processUserInput();
    }

    private static Thread setupServerMessageListener() {
        return new Thread(() -> {
            try {
                String serverMessage;
                while ((serverMessage = serverReader.readLine()) != null) {
                    messageManager.handleServerMessage(serverMessage);
                }
            } catch (IOException e) {
                System.out.println("Connection to server lost: " + e.getMessage());
            }
            closeConnection();
            System.exit(0);
        });
    }

    private static void processUserInput() throws IOException {
        while (true) {
            String input = userReader.readLine();
            if (input.startsWith("/dm")) {
                String[] parts = input.split(" ", 3);
                if (parts.length < 3) {
                    System.out.println("Invalid format. Use /dm <username> <message>");
                    continue;
                }
                String receiver = parts[1];
                String messageContent = parts[2];
                messageManager.sendPrivateMessage(receiver, messageContent);
            } else if (input.startsWith("/send")) {
                initiateFileTransferRequest(input);
            } else if (input.startsWith("/a ")) {
                handleFileTransferDecision(input, true);
            } else if (input.startsWith("/d ")) {
                handleFileTransferDecision(input, false);
            } else if(input.startsWith("/tttmove")){
                handleTicTacToeMove(input);
            }else {
                switch (input) {
                    case "/exit" -> {
                        messageManager.sendBye();
                        closeConnection();
                    }
                    case "/help" -> showHelpMenu();
                    case "/all" -> messageManager.requestClientList();
                    case "/rps" -> startRockPaperScissorsGame();
                    case "/y" -> messageManager.respondToRpsInvitation(true);
                    case "/n" -> messageManager.respondToRpsInvitation(false);
                    case "/yes" -> messageManager.respondToTttInvitation(true);
                    case "/no" -> messageManager.respondToTttInvitation(false);
                    case "/r" -> messageManager.sendRpsMove("/r");
                    case "/p" -> messageManager.sendRpsMove("/p");
                    case "/s" -> messageManager.sendRpsMove("/s");
                    case "/files" -> fileTransferManager.displayPendingRequests();
                    case "/ttt" -> startTicTacToeGame();
                    default -> messageManager.sendBroadcastMessage(input);
                }
            }
        }
    }

    private static void initiateFileTransferRequest(String input) throws JsonProcessingException {
        String[] parts = input.split(" ", 3);
        if (parts.length != 3) {
            System.out.println("Invalid command. Use: /send-file <receiver> <file-path>");
            return;
        }

        String receiver = parts[1];
        String filePath = parts[2];
        File file = new File(filePath);

        if (!file.exists() || !file.isFile()) {
            System.out.println("File does not exist or is invalid");
            return;
        }

        String filename = file.getName();
        String checksum = Utils.calculateFileChecksum(filePath);

        fileTransferManager.registerFilePath(filename, filePath);
        messageManager.sendFileTransferRequest(receiver, filename, checksum);
    }

    private static void handleFileTransferDecision(String input, boolean accept) throws JsonProcessingException {
        String[] parts = input.split(" ", 3);
        if (parts.length != 3) {
            System.out.println("Invalid command. Use /accept <sender> <filename> or /decline <sender> <filename>.");
            return;
        }

        String sender = parts[1];
        String filename = parts[2];
        FileTransferReq request = fileTransferManager.removeIncomingRequest(filename);

        if (request == null) {
            System.out.println("No file request found.");
            return;
        }

        if (accept) {
            System.out.println("Accepted file " + filename + " from " + sender);
            messageManager.sendFileTransferResponse(true);
        } else {
            System.out.println("Declined file " + filename + " from " + sender);
            messageManager.sendFileTransferResponse(false);
        }
    }

    private static void startRockPaperScissorsGame() throws IOException {
        messageManager.requestClientList();

        System.out.println("\nEnter your opponent: ");
        String opponent = userReader.readLine();
        messageManager.sendRpsStartRequest(opponent);
    }
    private static void startTicTacToeGame() throws IOException {
        messageManager.requestClientList();

        System.out.println("\nEnter your opponent: ");
        String opponent = userReader.readLine();
        messageManager.sendTttStartRequest(opponent);
    }

    private static void handleTicTacToeMove(String input) throws JsonProcessingException {
        String[] parts = input.split(" ");
        if (parts.length != 3) {
            System.out.println("Invalid command. Use /ttt <row> <col>");
            return;
        }

        int row = Integer.parseInt(parts[1]);
        int col = Integer.parseInt(parts[2]);
        messageManager.sendTttMove(row, col);
    }

    private static void showHelpMenu() {
        System.out.println("---------------------------------------------------------------------");
        System.out.println("/help - Show this help menu");
        System.out.println("/exit - Exit the chatroom");
        System.out.println("/all - Show all connected clients");
        System.out.println("@username <message> - Send a private message to a user");
        System.out.println("/rps - Start a Rock, Paper, Scissors game");
        System.out.println("/send <username> <file-path> - Request to send a file to another user");
        System.out.println("/files - Show all incoming file requests");
        System.out.println("/a <username> <filename> - Accept a file transfer request");
        System.out.println("/d <username> <filename> - Decline a file transfer request");
        System.out.println("/ttt - Start a Tic-Tac-Toe game");
        System.out.println("/tttmove <row> <col> - Make a move in Tic-Tac-Toe");
        System.out.println("Type a message to broadcast to the chatroom.");
        System.out.println("---------------------------------------------------------------------");

    }

    private static void closeConnection() {
        try {
            if (writer != null) writer.close();
            if (serverReader != null) serverReader.close();
            if (socket != null && !socket.isClosed()) socket.close();
            System.out.println("All resources closed successfully.");
        } catch (IOException e) {
            System.out.println("Error while closing resources: " + e.getMessage());
        }
    }

    private static void sendServerCommand(String command, Object message) throws JsonProcessingException {
        String jsonMessage = command + " " + (message == null ? "{}" : mapper.writeValueAsString(message));
        writer.println(jsonMessage);
    }
}