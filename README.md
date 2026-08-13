# Java File-Backed DNS Server

A lightweight, custom DNS server built entirely from scratch in Java. This project demonstrates how to handle raw UDP network sockets and parse bit-level DNS protocol packets (RFC 1034/1035) without relying on any external networking libraries.

It intercepts standard DNS queries, looks up the requested domain in a local text-based database, and constructs a valid binary DNS response containing the mapped IPv4 address.

## Features
* **Zero Dependencies:** Uses only standard Java `java.net` and `java.nio` libraries.
* **File-Backed Database:** Easily map domains to IP addresses using a simple `.txt` file.
* **Protocol Compliant:** Correctly handles DNS packet flags, transaction IDs, and utilizes DNS pointer compression (`0xC00C`) for efficient responses.
* **Graceful Fallbacks:** Properly handles unsupported queries (like IPv6 `AAAA` or `PTR` reverse lookups) by returning safe, non-crashing DNS flags.
* **Cross-Platform:** Works natively with Linux `dig` and Windows `nslookup`.

## Prerequisites
* Java Development Kit (JDK) 11 or higher.

## Setup & Execution

1. **Clone the repository:**
```bash
git clone https://github.com/yourusername/java-dns-server.git
cd java-dns-server
```

2. **Compile the server:**
```bash
javac FileBackedDNSServer.java
```

3. **Run the server:**
```bash
java FileBackedDNSServer
```
*The server runs on port `1053` by default to avoid requiring root/administrator privileges.*

## Database Configuration

On the first run, the server will automatically generate a `dns_records.txt` file in the same directory. You can add your custom domain mappings to this file using the `domain=IP` format:

```text
# dns_records.txt
example.com=93.184.216.34
mytest.local=192.168.1.50
custom.server.net=10.0.0.42
```
*Note: Restart the server after modifying the text file to load the new records into memory.*

## Testing the Server

You can query the server using standard network tools. Keep the server running in one terminal window, and open a second terminal to run these tests.

**On Linux / macOS (using `dig`):**
```bash
dig @127.0.0.1 -p 1053 example.com
```

**On Windows (using `nslookup` in CMD/PowerShell):**
```cmd
nslookup -port=1053 example.com 127.0.0.1
```

### Expected Output
If successful, the command-line tool will return the IP address specified in your `dns_records.txt` file, and your server console will log:
```text
-> CMD Query received: [example.com] Type: A (IPv4)
```

## How It Works Under the Hood
1. **Listens on UDP 1053:** The server opens a `DatagramSocket` and waits for 512-byte packets.
2. **Parses the 12-byte Header:** Extracts the randomly generated Transaction ID and query flags.
3. **Decodes the Domain Name:** Reads the length-prefixed domain format (e.g., converting `[7]example[3]com[0]` into `example.com`).
4. **Constructs the Response:** If a match is found in the HashMap, it builds a new byte array, sets the "Authoritative Answer" flag (`0x8580`), echoes the question, and appends the 4-byte IPv4 address as an `A Record`.
