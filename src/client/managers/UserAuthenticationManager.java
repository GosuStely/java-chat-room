package client.managers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import Utilities.Commands;
import Utilities.messages.Enter;
import Utilities.messages.EnterResp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

public class UserAuthenticationManager {
    private BufferedReader userReader;
    private BufferedReader serverReader;
    private PrintWriter writer;
    private ObjectMapper mapper;

    public UserAuthenticationManager(BufferedReader userReader, BufferedReader serverReader, PrintWriter writer, ObjectMapper mapper) {
        this.userReader = userReader;
        this.serverReader = serverReader;
        this.writer = writer;
        this.mapper = mapper;
    }

    public String authenticateUser() throws IOException {
        while (true) {
            System.out.print("Enter username: ");
            String usernameInput = userReader.readLine();

            Enter enterMessage = new Enter(usernameInput);
            sendCommand(Commands.ENTER, enterMessage);

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
                return usernameInput;
            }

            displayLoginError(enterResp.code());
        }
    }

    private void displayLoginError(int errorCode) {
        switch (errorCode) {
            case 5000 -> System.out.println("User with this name already exists.");
            case 5001 -> System.out.println("A username may only consist of 3-14 characters, numbers, and underscores.");
            case 5002 -> System.out.println("User is already logged in.");
            default -> System.out.println("Unknown error occurred.");
        }
    }

    private void sendCommand(String command, Object message) throws JsonProcessingException {
        String jsonMessage = command + " " + (message == null ? "{}" : mapper.writeValueAsString(message));
        writer.println(jsonMessage);
    }
}