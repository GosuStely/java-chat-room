package Utilities.messages.fileTransfer;

public record FileTransferReq(String sender, String receiver, String filename, String checksum) {
}
