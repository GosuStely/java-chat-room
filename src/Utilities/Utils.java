package Utilities;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;

public class Utils {
    public static final int SERVER_PORT = 1234;
    public static final int FILE_TRANSFER_PORT = 1235;

    public static String calculateFileChecksum(String filePath) {
        try {
            byte[] data = Files.readAllBytes(Paths.get(filePath));
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            return new BigInteger(1, hash).toString(16);
        } catch (Exception e) {
            System.out.println(STR."Failed to calculate checksum\{e.getMessage()}");
        }
        return "-1";
    }
}
