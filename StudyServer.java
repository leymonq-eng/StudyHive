package com.mycompany.studyhive;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Multi-client TCP server for a hosted StudyHive session.
 * Tracks room start time, active peers, board history, replies, and question resolution state.
 */
public class StudyServer extends Thread {

    private final int port;
    private final long sessionStartEpochSec;
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    private final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, StudyModels.BoardEntry> boardHistory =
            Collections.synchronizedMap(new LinkedHashMap<>());

    /**
     * Constructs a StudyServer on the specified TCP port and records session start time.
     */
    public StudyServer(int port) {
        super("StudyHive-Server-" + port);
        this.port = port;
        this.sessionStartEpochSec = System.currentTimeMillis() / 1000L;
        setDaemon(true);
    }

    /**
     * Returns elapsed session duration in seconds since the host created the room.
     */
    public long getElapsedSessionSeconds() {
        return Math.max(0L, (System.currentTimeMillis() / 1000L) - sessionStartEpochSec);
    }

    /**
     * Binds the ServerSocket and accepts incoming peer connections until stopped.
     */
    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            while (running) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket);
                handler.start();
            }
        } catch (IOException ignored) {
            // Expected when serverSocket is closed during stopServer()
        }
    }

    /**
     * Stops accepting connections, closes active client sockets, and releases the server port.
     */
    public void stopServer() {
        running = false;
        synchronized (clients) {
            for (ClientHandler ch : new ArrayList<>(clients)) {
                ch.closeQuietly();
            }
            clients.clear();
        }
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * Checks case-insensitively whether a nickname is already registered in the session.
     */
    private boolean isNicknameTaken(String nick) {
        synchronized (clients) {
            for (ClientHandler ch : clients) {
                if (ch.nickname != null && ch.nickname.equalsIgnoreCase(nick)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Sends a protocol line to every currently connected client.
     */
    private void broadcast(String frame) {
        synchronized (clients) {
            for (ClientHandler ch : new ArrayList<>(clients)) {
                ch.sendLine(frame);
            }
        }
    }

    /**
     * Broadcasts the updated list of connected peer nicknames to all clients.
     */
    private void broadcastPeerList() {
        List<String> names = new ArrayList<>();
        synchronized (clients) {
            for (ClientHandler ch : clients) {
                if (ch.nickname != null) {
                    names.add(StudyModels.encodeField(ch.nickname));
                }
            }
        }
        broadcast("PEERS|" + String.join(",", names));
    }

    /**
     * Handles a single connected peer's handshake, incoming frames, and disconnect cleanup.
     */
    private class ClientHandler extends Thread {
        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String nickname;
        private String avatar = "CAT";

        /**
         * Creates a daemon handler thread for a connected peer socket.
         */
        ClientHandler(Socket socket) {
            super("StudyHive-ClientHandler");
            this.socket = socket;
            setDaemon(true);
        }

        /**
         * Thread-safely writes a single protocol frame line to this client.
         */
        synchronized void sendLine(String line) {
            if (out != null) {
                out.println(line);
                out.flush();
            }
        }

        /**
         * Closes the client socket without throwing.
         */
        void closeQuietly() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }

        /**
         * Processes the JOIN handshake, syncs session history, and loops over client messages.
         */
        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

                String joinLine = in.readLine();
                if (joinLine == null || !joinLine.startsWith("JOIN|")) {
                    closeQuietly();
                    return;
                }

                String[] jp = joinLine.split("\\|", -1);
                String requestedNick = jp.length > 1 ? StudyModels.decodeField(jp[1]).trim() : "";
                String requestedAvatar = jp.length > 2 ? StudyModels.decodeField(jp[2]).trim() : "CAT";

                if (requestedNick.isEmpty()) {
                    sendLine("ERROR|" + StudyModels.encodeField("Nickname cannot be blank."));
                    closeQuietly();
                    return;
                }

                synchronized (clients) {
                    if (isNicknameTaken(requestedNick)) {
                        sendLine("ERROR|" + StudyModels.encodeField("Nickname '" + requestedNick + "' is already in use."));
                        closeQuietly();
                        return;
                    }
                    this.nickname = requestedNick;
                    this.avatar = requestedAvatar.isEmpty() ? "CAT" : requestedAvatar;
                    clients.add(this);
                }

                // WELCOME|<nick>|<elapsedSessionSeconds>
                sendLine("WELCOME|" + StudyModels.encodeField(this.nickname) + "|" + getElapsedSessionSeconds());

                synchronized (boardHistory) {
                    for (StudyModels.BoardEntry entry : boardHistory.values()) {
                        sendLine(formatEntryFrame(entry));
                        for (StudyModels.ResponseItem resp : entry.responses) {
                            sendLine("REPLY|" + entry.id + "|"
                                        + StudyModels.encodeField(resp.author) + "|"
                                        + StudyModels.encodeField(resp.text) + "|"
                                        + StudyModels.encodeField(resp.timestamp));
                        }
                    }
                }

                broadcastPeerList();
                broadcast("SYS|JOIN|" + StudyModels.encodeField(this.nickname + " joined the session."));

                String line;
                while ((line = in.readLine()) != null) {
                    handleClientFrame(line);
                }
            } catch (IOException ignored) {
            } finally {
                boolean removed;
                synchronized (clients) {
                    removed = clients.remove(this);
                }
                if (removed && nickname != null) {
                    broadcastPeerList();
                    broadcast("SYS|LEAVE|" + StudyModels.encodeField(nickname + " left the session."));
                }
                closeQuietly();
            }
        }

        /**
         * Dispatches a decoded client protocol frame (CHAT, CMD, NOTE, QUESTION, MEDIA, REPLY, RESOLVE).
         */
        private void handleClientFrame(String line) {
            String[] parts = line.split("\\|", -1);
            if (parts.length == 0) return;

            switch (parts[0]) {
                case "CHAT" -> {
                    if (parts.length < 2) return;
                    String msg = StudyModels.decodeField(parts[1]).trim();
                    if (msg.isEmpty()) return;
                    broadcast("CHAT|" + StudyModels.encodeField(nickname) + "|"
                                + StudyModels.encodeField(avatar) + "|"
                                + StudyModels.encodeField(StudyModels.nowTime()) + "|"
                                + StudyModels.encodeField(msg));
                }
                case "CMD" -> {
                    if (parts.length < 2) return;
                    String cmd = StudyModels.decodeField(parts[1]).trim();
                    if (cmd.equalsIgnoreCase("/exit")) {
                        closeQuietly();
                    } else if (cmd.toLowerCase().startsWith("/name ")) {
                        String newNick = cmd.substring(6).trim();
                        if (newNick.isEmpty()) {
                            sendLine("SYS|INFO|" + StudyModels.encodeField("New nickname cannot be blank."));
                            return;
                        }
                        synchronized (clients) {
                            if (isNicknameTaken(newNick)) {
                                sendLine("SYS|INFO|" + StudyModels.encodeField("Nickname '" + newNick + "' is already taken."));
                                return;
                            }
                            String oldNick = this.nickname;
                            this.nickname = newNick;
                            broadcastPeerList();
                            broadcast("SYS|INFO|" + StudyModels.encodeField(oldNick + " changed nickname to " + newNick + "."));
                        }
                    } else {
                        sendLine("SYS|INFO|" + StudyModels.encodeField("Unknown command. Use /name <newname>, /exit, or /help."));
                    }
                }
                case "NOTE", "QUESTION" -> {
                    if (parts.length < 2) return;
                    String text = StudyModels.decodeField(parts[1]).trim();
                    if (text.isEmpty()) return;
                    StudyModels.EntryType type = parts[0].equals("NOTE")
                            ? StudyModels.EntryType.NOTE : StudyModels.EntryType.QUESTION;
                    String id = UUID.randomUUID().toString();
                    StudyModels.BoardEntry entry = new StudyModels.BoardEntry(
                                id, type, nickname, text, "", "", StudyModels.nowTime(), false
                    );
                    boardHistory.put(id, entry);
                    broadcast(formatEntryFrame(entry));
                    String postedLabel = type == StudyModels.EntryType.NOTE
                            ? nickname + " posted a Note"
                            : nickname + " posted a Question";
                    broadcast("SYS|POST|" + StudyModels.encodeField(postedLabel));
                }
                case "MEDIA" -> {
                    if (parts.length < 5) return;
                    StudyModels.EntryType type = parts[1].equals("IMAGE")
                            ? StudyModels.EntryType.IMAGE : StudyModels.EntryType.FILE;
                    String fileName = StudyModels.decodeField(parts[2]);
                    String sizeLabel = StudyModels.decodeField(parts[3]);
                    String payloadB64 = parts[4];
                    String id = UUID.randomUUID().toString();
                    StudyModels.BoardEntry entry = new StudyModels.BoardEntry(
                                id, type, nickname, sizeLabel, fileName, payloadB64, StudyModels.nowTime(), false
                    );
                    boardHistory.put(id, entry);
                    broadcast(formatEntryFrame(entry));
                }
                case "REPLY" -> {
                    if (parts.length < 3) return;
                    String entryId = parts[1];
                    String replyText = StudyModels.decodeField(parts[2]).trim();
                    if (replyText.isEmpty()) return;
                    StudyModels.BoardEntry target = boardHistory.get(entryId);
                    if (target != null && target.type == StudyModels.EntryType.QUESTION) {
                        String ts = StudyModels.nowTime();
                        target.responses.add(new StudyModels.ResponseItem(nickname, replyText, ts));
                        broadcast("REPLY|" + entryId + "|"
                                    + StudyModels.encodeField(nickname) + "|"
                                    + StudyModels.encodeField(replyText) + "|"
                                    + StudyModels.encodeField(ts));
                    }
                }
                case "RESOLVE" -> {
                    if (parts.length < 3) return;
                    String entryId = parts[1];
                    boolean resolved = Boolean.parseBoolean(parts[2]);
                    StudyModels.BoardEntry target = boardHistory.get(entryId);
                    if (target != null && target.type == StudyModels.EntryType.QUESTION) {
                        target.resolved = resolved;
                        broadcast("RESOLVE|" + entryId + "|" + resolved);
                    }
                }
            }
        }

        /**
         * Serializes a BoardEntry into a wire-safe ENTRY frame.
         */
        private String formatEntryFrame(StudyModels.BoardEntry e) {
            return "ENTRY|" + e.id + "|" + e.type.name() + "|"
                    + StudyModels.encodeField(e.author) + "|"
                    + StudyModels.encodeField(e.content) + "|"
                    + StudyModels.encodeField(e.fileName) + "|"
                    + (e.base64Data != null ? e.base64Data : "") + "|"
                    + StudyModels.encodeField(e.timestamp) + "|"
                    + e.resolved;
        }
    }
}
