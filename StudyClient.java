package com.mycompany.studyhive;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * TCP client for StudyHive that manages connection handshake, outgoing commands/entries,
 * and a background reader thread dispatching events to the UI listener.
 */
public class StudyClient {

    /**
     * Callback interface implemented by StudyHiveUI to receive server events safely.
     */
    public interface Listener {
        void onSessionSynced(long initialSessionElapsedSeconds);
        void onChatMessage(String sender, String avatar, String message, String timestamp);
        void onBoardEntryAdded(StudyModels.BoardEntry entry);
        void onBoardResponseAdded(String entryId, StudyModels.ResponseItem response);
        void onBoardEntryResolved(String entryId, boolean resolved);
        void onOnlinePeersUpdated(List<String> peers);
        void onSystemNotice(String category, String notice);
        void onDisconnected(String reason);
    }

    private final String host;
    private final int port;
    private final String nickname;
    private final String avatar;
    private final Listener listener;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private volatile boolean connected = false;

    /**
     * Initializes a StudyClient with target server address, user profile, and event listener.
     */
    public StudyClient(String host, int port, String nickname, String avatar, Listener listener) {
        this.host = host;
        this.port = port;
        this.nickname = nickname;
        this.avatar = avatar;
        this.listener = listener;
    }

    /**
     * Connects to the server, completes the JOIN/WELCOME handshake, and starts the reader thread.
     */
    public void connect() throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 4000);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

        sendRaw("JOIN|" + StudyModels.encodeField(nickname) + "|" + StudyModels.encodeField(avatar));

        String response = in.readLine();
        if (response == null) {
            closeQuietly();
            throw new IOException("Server closed connection during handshake.");
        }
        if (response.startsWith("ERROR|")) {
            String errMsg = StudyModels.decodeField(response.substring("ERROR|".length()));
            closeQuietly();
            throw new IOException(errMsg);
        }

        long initialElapsed = 0L;
        if (response.startsWith("WELCOME|")) {
            String[] wp = response.split("\\|", -1);
            if (wp.length >= 3) {
                try {
                    initialElapsed = Long.parseLong(wp[2].trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        listener.onSessionSynced(initialElapsed);

        connected = true;
        Thread readerThread = new Thread(this::readLoop, "StudyHive-ClientReader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    /**
     * Continuously reads incoming protocol lines from the socket until disconnected.
     */
    private void readLoop() {
        try {
            String line;
            while (connected && (line = in.readLine()) != null) {
                dispatchFrame(line);
            }
            if (connected) {
                connected = false;
                listener.onDisconnected("Server closed the session.");
            }
        } catch (IOException ex) {
            if (connected) {
                connected = false;
                listener.onDisconnected("Lost connection to server.");
            }
        }
    }

    /**
     * Parses a single incoming protocol frame and invokes the matching listener callback.
     */
    private void dispatchFrame(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length == 0) return;

        switch (parts[0]) {
            case "CHAT" -> {
                if (parts.length >= 5) {
                    listener.onChatMessage(
                                StudyModels.decodeField(parts[1]),
                        StudyModels.decodeField(parts[2]),
                        StudyModels.decodeField(parts[4]),
                        StudyModels.decodeField(parts[3])
                    );
                }
            }
            case "ENTRY" -> {
                if (parts.length >= 9) {
                    StudyModels.EntryType type = StudyModels.EntryType.valueOf(parts[2]);
                    StudyModels.BoardEntry entry = new StudyModels.BoardEntry(
                                parts[1],
                        type,
                        StudyModels.decodeField(parts[3]),
                        StudyModels.decodeField(parts[4]),
                        StudyModels.decodeField(parts[5]),
                        parts[6],
                        StudyModels.decodeField(parts[7]),
                        Boolean.parseBoolean(parts[8])
                    );
                    listener.onBoardEntryAdded(entry);
                }
            }
            case "REPLY" -> {
                if (parts.length >= 5) {
                    listener.onBoardResponseAdded(
                                parts[1],
                        new StudyModels.ResponseItem(
                                    StudyModels.decodeField(parts[2]),
                            StudyModels.decodeField(parts[3]),
                            StudyModels.decodeField(parts[4])
                        )
                    );
                }
            }
            case "RESOLVE" -> {
                if (parts.length >= 3) {
                    listener.onBoardEntryResolved(parts[1], Boolean.parseBoolean(parts[2]));
                }
            }
            case "PEERS" -> {
                List<String> peers = new ArrayList<>();
                if (parts.length >= 2 && !parts[1].isEmpty()) {
                    for (String enc : parts[1].split(",")) {
                        peers.add(StudyModels.decodeField(enc));
                    }
                }
                listener.onOnlinePeersUpdated(peers);
            }
            case "SYS" -> {
                if (parts.length >= 3) {
                    listener.onSystemNotice(parts[1], StudyModels.decodeField(parts[2]));
                } else if (parts.length >= 2) {
                    listener.onSystemNotice("INFO", StudyModels.decodeField(parts[1]));
                }
            }
        }
    }

    /**
     * Thread-safely sends a raw protocol line to the server.
     */
    private synchronized void sendRaw(String frame) {
        if (out != null) {
            out.println(frame);
            out.flush();
        }
    }

    /**
     * Sends a standard group chat message.
     */
    public void sendChat(String text) {
        sendRaw("CHAT|" + StudyModels.encodeField(text));
    }
    /**
     * Sends a slash command such as /name or /exit.
     */
    public void sendCommand(String cmd) {
        sendRaw("CMD|" + StudyModels.encodeField(cmd));
    }

    /**
     * Posts a study note to the synchronized board and stream.
     */
    public void sendNote(String text) {
        sendRaw("NOTE|" + StudyModels.encodeField(text));
    }

    /**
     * Posts a study question to the synchronized board and stream.
     */
    public void sendQuestion(String text) {
        sendRaw("QUESTION|" + StudyModels.encodeField(text));
    }

    /**
     * Uploads a Base64-encoded document/file attachment to the session.
     */
    public void sendFile(String fileName, String sizeLabel, String base64Data) {
        sendRaw("MEDIA|FILE|" + StudyModels.encodeField(fileName) + "|"
                    + StudyModels.encodeField(sizeLabel) + "|" + base64Data);
    }

    /**
     * Uploads a Base64-encoded image attachment to the session.
     */
    public void sendImage(String fileName, String sizeLabel, String base64Data) {
        sendRaw("MEDIA|IMAGE|" + StudyModels.encodeField(fileName) + "|"
                    + StudyModels.encodeField(sizeLabel) + "|" + base64Data);
    }

    /**
     * Submits a peer response to a specific question entry.
     */
    public void sendResponse(String entryId, String replyText) {
        sendRaw("REPLY|" + entryId + "|" + StudyModels.encodeField(replyText));
    }

    /**
     * Updates whether a question entry is resolved/marked right.
     */
    public void sendResolve(String entryId, boolean resolved) {
        sendRaw("RESOLVE|" + entryId + "|" + resolved);
    }

    /**
     * Marks the client disconnected and closes the socket quietly.
     */
    public void closeQuietly() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }
}
