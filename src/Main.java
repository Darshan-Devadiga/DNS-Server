import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    private static final int PORT = 1053;
    private static final Map<String, String> dnsDatabase = new HashMap<>();

    public static void main(String[] args) {
        loadDatabase("dns_records.txt");

        // Binding only to the PORT means it listens on ALL interfaces (IPv4 and IPv6)
        try (DatagramSocket socket = new DatagramSocket(PORT)) {
            System.out.println("✅ DNS Server running on all interfaces (0.0.0.0:" + PORT + ")");
            byte[] buffer = new byte[512];

            while (true) {
                try {
                    DatagramPacket requestPacket = new DatagramPacket(buffer, buffer.length);
                    socket.receive(requestPacket);

                    byte[] responseBytes = handleQuery(requestPacket.getData(), requestPacket.getLength());

                    DatagramPacket responsePacket = new DatagramPacket(
                            responseBytes, responseBytes.length,
                            requestPacket.getAddress(), requestPacket.getPort()
                    );
                    socket.send(responsePacket);
                } catch (Exception e) {
                    System.err.println("⚠️ Error processing a packet: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Fatal server startup error: " + e.getMessage());
        }
    }

    private static void loadDatabase(String filename) {
        try {
            File file = new File(filename);
            if (!file.exists()) {
                Files.writeString(file.toPath(), "example.com=93.184.216.34\nmytest.local=192.168.1.50\n");
            }
            List<String> lines = Files.readAllLines(file.toPath());
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("=");
                if (parts.length == 2) {
                    dnsDatabase.put(parts[0].trim().toLowerCase(), parts[1].trim());
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load database: " + e.getMessage());
        }
    }

    private static byte[] handleQuery(byte[] requestData, int length) {
        ByteBuffer reqBuffer = ByteBuffer.wrap(requestData, 0, length);
        short transactionId = reqBuffer.getShort();
        short flags = reqBuffer.getShort();
        short qdCount = reqBuffer.getShort();
        reqBuffer.position(12);

        StringBuilder domainBuilder = new StringBuilder();
        int currentPos = 12;

        while (currentPos < length) {
            int labelLength = requestData[currentPos] & 0xFF;
            if (labelLength == 0) { currentPos++; break; }
            if ((labelLength & 0xC0) == 0xC0) { currentPos += 2; break; }
            currentPos++;
            for (int i = 0; i < labelLength && currentPos < length; i++) {
                domainBuilder.append((char) requestData[currentPos++]);
            }
            domainBuilder.append(".");
        }

        String domain = domainBuilder.length() > 0 ? domainBuilder.substring(0, domainBuilder.length() - 1).toLowerCase() : "";

        reqBuffer.position(currentPos);
        int qType = reqBuffer.getShort() & 0xFFFF;
        int questionEndPos = reqBuffer.position();

        String typeName = (qType == 1) ? "A (IPv4)" : (qType == 28) ? "AAAA (IPv6)" : (qType == 12) ? "PTR (Reverse)" : String.valueOf(qType);
        System.out.println("-> CMD Query received: [" + domain + "] Type: " + typeName);

        String ipAddress = dnsDatabase.get(domain);
        boolean domainExists = dnsDatabase.containsKey(domain);

        ByteBuffer respBuffer = ByteBuffer.allocate(512);
        respBuffer.putShort(transactionId);

        if (domainExists && qType == 1) {
            // 0x8580 = Standard response, Authoritative Answer, No error (CMD likes this)
            respBuffer.putShort((short) 0x8580);
            respBuffer.putShort(qdCount);
            respBuffer.putShort((short) 1);
            respBuffer.putShort((short) 0);
            respBuffer.putShort((short) 0);

            respBuffer.put(requestData, 12, questionEndPos - 12);

            respBuffer.putShort((short) 0xC00C);
            respBuffer.putShort((short) 1);
            respBuffer.putShort((short) 1);
            respBuffer.putInt(60);
            respBuffer.putShort((short) 4);

            for (String part : ipAddress.split("\\.")) {
                respBuffer.put((byte) Integer.parseInt(part));
            }
        } else if (domainExists) {
            respBuffer.putShort((short) 0x8580);
            respBuffer.putShort(qdCount);
            respBuffer.putShort((short) 0);
            respBuffer.putShort((short) 0);
            respBuffer.putShort((short) 0);
            respBuffer.put(requestData, 12, questionEndPos - 12);
        } else {
            respBuffer.putShort((short) 0x8183);
            respBuffer.putShort(qdCount);
            respBuffer.putShort((short) 0);
            respBuffer.putShort((short) 0);
            respBuffer.putShort((short) 0);
            respBuffer.put(requestData, 12, questionEndPos - 12);
        }

        int respLength = respBuffer.position();
        byte[] responseData = new byte[respLength];
        System.arraycopy(respBuffer.array(), 0, responseData, 0, respLength);
        return responseData;
    }
}