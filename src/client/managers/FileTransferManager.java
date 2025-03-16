package client.managers;

import Utilities.Utils;
import Utilities.messages.fileTransfer.FileTransferReady;
import Utilities.messages.fileTransfer.FileTransferReq;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileTransferManager {
    private final String serverAddress;
    private final Map<String, String> filePathMap;
    private final List<FileTransferReq> incomingRequests;

    public FileTransferManager(String serverAddress) {
        this.serverAddress = serverAddress;
        this.filePathMap = new HashMap<>();
        this.incomingRequests = new ArrayList<>();
    }

    /**
     * Handles the file transfer process after receiving a `FILE_TRANSFER_READY` command from the server.
     * Starts a new thread for sending or receiving the file.
     *
     * @param fileTransferReady The object containing file transfer details.
     */
    public void handleFileTransfer(FileTransferReady fileTransferReady) {
        String uuid = fileTransferReady.uuid();
        String type = fileTransferReady.type();
        String checksum = fileTransferReady.checksum();
        String filename = fileTransferReady.filename();

        new Thread(() -> {
            try (Socket transferSocket = new Socket(serverAddress, Utils.FILE_TRANSFER_PORT);
                 InputStream transferInputStream = transferSocket.getInputStream();
                 OutputStream transferOutputStream = transferSocket.getOutputStream()) {

                if (type.equals("s")) {
                    String path = filePathMap.get(filename);
                    if (path == null) {
                        System.out.println("No stored path for filename " + filename);
                        return;
                    }
                    transferFileToServer(uuid, path, transferOutputStream);
                } else if (type.equals("r")) {
                    downloadFileFromServer(uuid, checksum, filename, transferInputStream, transferOutputStream);
                }
            } catch (IOException e) {
                System.out.println("File transfer error: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Sends a file to the server as part of a file transfer process.
     *
     * @param uuid The unique identifier for the file transfer.
     * @param filePath The path to the file being sent.
     * @param transferOutputStream The output stream for sending the file data.
     * @throws IOException If an error occurs during file transfer.
     */
    private void transferFileToServer(String uuid, String filePath, OutputStream transferOutputStream) throws IOException {
        String header = uuid + "s";
        transferOutputStream.write(header.getBytes());
        transferOutputStream.flush();

        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            System.out.println("Invalid file.");
            return;
        }

        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            fileInputStream.transferTo(transferOutputStream);
        } catch (IOException e) {
            System.out.println("Error while sending file: " + e.getMessage());
        }

        transferOutputStream.close();
        System.out.println("All file bytes sent successfully. Sender's socket closed");
    }

    /**
     * Receives a file from the server and saves it locally.
     * Ensures that the file has a unique name to avoid overwriting existing files.
     *
     * @param uuid The unique identifier for the file transfer.
     * @param expectedChecksum The expected checksum of the file for validation.
     * @param filename The original name of the file.
     * @param transferInputStream The input stream for receiving the file data.
     * @param transferOutputStream The output stream for sending control data.
     * @throws IOException If an error occurs during file transfer or saving.
     */
    private void downloadFileFromServer(String uuid, String expectedChecksum, String filename,
                                        InputStream transferInputStream,
                                        OutputStream transferOutputStream) throws IOException {
        String header = uuid + "r";
        transferOutputStream.write(header.getBytes());
        transferOutputStream.flush();

        File downloadDir = new File("src\\client\\downloadedFiles");
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
            System.out.println("New path created at: " + downloadDir.getAbsolutePath());
        }

        File outFile = createUniqueFileName(downloadDir, filename);
        try (FileOutputStream fileOutputStream = new FileOutputStream(outFile)) {
            transferInputStream.transferTo(fileOutputStream);
            System.out.println("Downloading...");

            System.out.println("Checking checksum...");
            if (expectedChecksum != null) {
                String actualChecksum = Utils.calculateFileChecksum(outFile.getPath());
                if (expectedChecksum.equals(actualChecksum)) {
                    System.out.println("File download complete. Saved to: " + outFile.getAbsolutePath());
                } else {
                    System.out.println("Checksum mismatch! Expected: " + expectedChecksum);
                    System.out.println("Actual: " + actualChecksum);
                }
            }
            closeFileTransferSockets(transferOutputStream, transferInputStream);
        } catch (IOException e) {
            System.out.println("Error writing downloaded file: " + e.getMessage());
        }
    }

    /**
     * Generates a unique file in the target directory by appending a numeric suffix if necessary.
     *
     * @param directory The directory where the file will be saved.
     * @param filename  The original filename.
     * @return A File object with a unique filename.
     */
    private File createUniqueFileName(File directory, String filename) {
        String baseName = filename;
        String extension = "";

        // Split the filename into base and extension
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
            baseName = filename.substring(0, lastDotIndex);
            extension = filename.substring(lastDotIndex);
        }

        File uniqueFile = new File(directory, filename);
        int count = 1;

        // Keep incrementing the suffix until the filename is unique
        while (uniqueFile.exists()) {
            String newFilename = baseName + "(" + count + ")" + extension;
            uniqueFile = new File(directory, newFilename);
            count++;
        }

        return uniqueFile;
    }

    /**
     * Prepares for a file transfer by storing the file path for later use.
     *
     * @param filename The name of the file.
     * @param filePath The full path to the file.
     */
    public void registerFilePath(String filename, String filePath) {
        filePathMap.put(filename, filePath);
    }

    /**
     * Adds an incoming file transfer request to the list of pending requests.
     *
     * @param request The file transfer request to add.
     */
    public void addIncomingRequest(FileTransferReq request) {
        incomingRequests.add(request);
    }

    /**
     * Removes a file transfer request from the list of pending requests.
     *
     * @param filename The name of the file to remove.
     * @return The removed request, or null if not found.
     */
    public FileTransferReq removeIncomingRequest(String filename) {
        FileTransferReq request = null;
        for (FileTransferReq req : incomingRequests) {
            if (req.filename().equals(filename)) {
                request = req;
                break;
            }
        }

        if (request != null) {
            incomingRequests.remove(request);
        }

        return request;
    }

    /**
     * Displays all pending file transfer requests.
     */
    public void displayPendingRequests() {
        if (incomingRequests.isEmpty()) {
            System.out.println("No request to show.");
        } else {
            System.out.println("--- File requests ---");
            int counter = 1;
            for (FileTransferReq req : incomingRequests) {
                System.out.println(counter + ". From: " + req.sender() + ", filename: " + req.filename());
                counter++;
            }
        }
    }

    /**
     * Closes the input and output streams used for file transfer.
     *
     * @param os The output stream to close.
     * @param is The input stream to close.
     */
    private void closeFileTransferSockets(OutputStream os, InputStream is) {
        try {
            os.close();
            is.close();
            System.out.println("File transfer sockets closed successfully.");
        } catch (IOException e) {
            System.out.println("Error while closing file transfer sockets: " + e.getMessage());
        }
    }
}