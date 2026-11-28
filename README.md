# StudyHive: Collaborative Networked Study Space

## Table of Contents

- [1. Header & Metadata](#1-header--metadata)
- [2. Team Members & GitHub Accounts](#2-team-members--github-accounts)
- [3. Project Overview, Problem & Theme](#3-project-overview-problem--theme)
- [4. Architecture & Message Protocol](#4-architecture--message-protocol)
- [5. Setup, Execution & Troubleshooting](#5-setup-execution--troubleshooting)
- [6. Test Cases & Results](#6-test-cases--results)
- [7. Labeled Screenshots](#7-labeled-screenshots)
- [8. Pair Contribution Log](#8-pair-contribution-log)
- [Known Limitations](#known-limitations)

## 1. Header & Metadata

- **Course & Section:** ITAS1 — Network Programming | Section BSIT 2B-SD
- **Repository Name:** `ITAS1-BSIT-2B-SD-Vallar-Gumarang`
- **Submission Date:** November 27, 2026

## 2. Team Members & GitHub Accounts

- **Member 1:** Jade Vincent Suobiron Vallar — jadevincentvallar
- **Member 2:** Krisley Son U. Gumarang — leymonq-eng

## 3. Project Overview, Problem & Theme

### User Problem

During virtual study sessions, students frequently struggle with context switching between distinct applications for real-time text discussion, topic-tagged note sharing, and session media exchange. Standard chat clients often collapse code snippets, quick questions, and resource links into a single unorganized stream, causing important study queries to get lost.

### Concept & Approval

StudyHive is a lightweight multi-client Java desktop application engineered specifically for collaborative study groups. It operates on a centralized server architecture providing low-latency TCP communication, interactive study modules, and structured real-time media exchange.

### Core & Original Theme Features

- **Real-time Peer Messaging:** Socket communication with join/leave notifications and a live online peer list.
- **Tagged Study Board (Notes & Questions):** A synchronized board where peers post items tagged as **Note** or **Question**, reply to questions, and mark them resolved. Topics such as `#JAVA` or `#EXAM` can be typed in the text and found with keyword search.
- **Session Media & File Sharing:** Images and study documents sent over the socket, with an image lightbox preview and local saving.
- **Study Profiles:** Animal avatars or a custom picture.
- **Session Timers:** Session time for everyone, plus joined time for peers.

## 4. Architecture & Message Protocol

### System Architecture Diagram

```text
+------------------------------------------------------------------+
|                     StudyServer (Host app)                       |
|                                                                  |
|  ServerSocket :5000 ---> ClientHandler ---> Client list and      |
|  (accept thread)         (one thread        board history        |
|                           per client)       (synchronized)       |
+------------------------------------------------------------------+
               ^                                  ^
               |          TCP socket I/O          |
               v                                  v
+-----------------------------+    +-----------------------------+
|        StudyClient 1        |    |        StudyClient 2        |
|        Reader thread        |    |        Reader thread        |
|              |              |    |              |              |
|  SwingUtilities.invokeLater |    |  SwingUtilities.invokeLater |
|              v              |    |              v              |
|      StudyHiveUI (EDT)      |    |      StudyHiveUI (EDT)      |
+-----------------------------+    +-----------------------------+
```

The host runs the server inside the same app and connects to it as a client through `127.0.0.1`.

### Architectural Components & Thread Safety

- **StudyServer:** Binds a `ServerSocket` to the configured port. For each accepted connection, it starts a dedicated `ClientHandler` thread.
- **StudyClient:** Manages one TCP `Socket` and a background reader thread that listens for server messages.
- **Thread-Safe Broadcasts:** The server locks the synchronized client list while broadcasting, and each handler's send method is `synchronized`.
- **Swing Thread Safety (`SwingUtilities.invokeLater`):** `StudyHiveUI` schedules incoming network events on the Event Dispatch Thread (EDT) before updating Swing components.

### Protocol & Message Format

Each message is one line with fields separated by pipes (`|`). Text fields are UTF-8 Base64 encoded, so they cannot break the format. IDs, entry types, numbers, and `true`/`false` values are sent as is.

**Client to server**

| Command | Frame Structure | Description |
|---|---|---|
| JOIN | `JOIN\|<nickname>\|<avatar>` | Requests to join the session |
| CHAT | `CHAT\|<message>` | Sends a chat message |
| CMD | `CMD\|<command_line>` | Sends `/name` or `/exit` |
| NOTE | `NOTE\|<text>` | Posts a note |
| QUESTION | `QUESTION\|<text>` | Posts a question |
| MEDIA | `MEDIA\|<IMAGE or FILE>\|<filename>\|<size>\|<base64_data>` | Shares an image or file |
| REPLY | `REPLY\|<entry_id>\|<text>` | Replies to a question |
| RESOLVE | `RESOLVE\|<entry_id>\|<true or false>` | Resolves or reopens a question |

**Server to client**

| Command | Frame Structure | Description |
|---|---|---|
| WELCOME | `WELCOME\|<nickname>\|<elapsed_seconds>` | Accepts the join request |
| ERROR | `ERROR\|<message>` | Rejects the join request |
| PEERS | `PEERS\|<nick1>,<nick2>,...` | Updates the online peer list |
| CHAT | `CHAT\|<nickname>\|<avatar>\|<time>\|<message>` | Broadcasts a chat message |
| ENTRY | `ENTRY\|<id>\|<type>\|<author>\|<content>\|<filename>\|<base64_data>\|<time>\|<resolved>` | Adds an item to the board |
| REPLY | `REPLY\|<entry_id>\|<author>\|<text>\|<time>` | Adds a reply to a question |
| RESOLVE | `RESOLVE\|<entry_id>\|<true or false>` | Updates a question's status |
| SYS | `SYS\|<category>\|<message>` | Sends a system notice |

## 5. Setup, Execution & Troubleshooting

### System Requirements

- **Java Development Kit (JDK):** Version 17 or higher
- **IDE:** Apache NetBeans 15+ (or any Java SE compatible IDE)
- **Build System:** Apache Ant (NetBeans)
- **Network:** Computers on the same local network

### Project Structure

```text
StudyHive/
├── docs/screenshots/
├── nbproject/
│   ├── project.properties
│   └── project.xml
├── src/com/mycompany/studyhive/
│   ├── Main.java           # Entry point and UI theme
│   ├── StudyModels.java    # Data models, utilities, avatars and logo
│   ├── StudyServer.java    # Multi-client TCP server
│   ├── StudyClient.java    # Client connection and events
│   └── StudyHiveUI.java    # Home and session screens
├── README.md
├── build.xml
└── manifest.mf
```

Optional images go in `src/avatars/` and `src/logo/`.

### Running the Application

1. **Start the Host:**
   - Open the project in Apache NetBeans.
   - Run `com.mycompany.studyhive.Main`.
   - Enter a nickname, check **Default Port** (`5000`), and click **Create a Session**.
   - **Connection Status** shows `Role: Host`, the IP address, and the port.
2. **Join as a Peer:**
   - Run `com.mycompany.studyhive.Main` again, on the same or another computer.
   - Enter a unique nickname.
   - Enter the host's **IP Address** (`127.0.0.1` on the same computer, otherwise the LAN IP) and **Server Port**.
   - Click **Join Session**.
   - Repeat to add more peers.

### Supported Client Commands

| Command | Syntax | Action / Result |
|---|---|---|
| Change Name | `/name <new_nickname>` | Changes the nickname on the server, notifies peers, and updates the peer list |
| Exit Session | `/exit` | Closes the connection and returns to Home. If the host exits, the session ends |
| Help Menu | `/help` | Shows the supported commands locally (the **Help** button does the same) |

### Troubleshooting Guide

- **Connection Refused:** Make sure the host has created the session first. Check the IP, port, and firewall.
- **Address Already in Use:** Another app or session is using the port. Choose a different one, such as `5001`.
- **Duplicate Nickname Error:** The server rejects a nickname already in use. Enter a different one.
- **`ClassNotFoundException` for `Main`:** Check that the main class is exactly `com.mycompany.studyhive.Main`.
- **UI Not Updating or Freezing:** Update Swing components through `SwingUtilities.invokeLater()`.

## 6. Test Cases & Results

Tested on November 27, 2026, with a scripted run of several app windows on one Linux machine, using a virtual display and `127.0.0.1`. The script called the app's actions directly instead of clicking. Testing across separate computers was not done.

| Test ID | Test Scenario | Inputs & Preconditions | Actual Result | Pass / Fail |
|---|---|---|---|---|
| TC-01 | Concurrent client connection | One host and two peers on `127.0.0.1:5000` | All three connected | PASS |
| TC-02 | Message broadcasting | Client1 sends "Hello Team" | Client2 and Client3 showed Client1's message | PASS |
| TC-03 | Dynamic peer list sync | Client3 joins Client1 and Client2 | All three windows listed all three users | PASS |
| TC-04 | Duplicate nickname validation | A fourth window joins as "Client2" | Rejected: "Nickname 'Client2' is already in use." | PASS |
| TC-05 | Input and address validation | IP `999.999.0.1`; port `abc` | Letters in the port were blocked. The invalid IP was not checked beforehand; the connection failed with an error dialog | PARTIAL |
| TC-06 | Unavailable server handling | Join while no host is running | "Connection refused" dialog, no crash | PASS |
| TC-07 | Graceful and abrupt disconnection | Client2 sends `/exit`; Client3's socket is closed without `/exit` | Host's peer list updated to Client1 only | PASS |
| TC-08 | Study board and media sync | Client1 posts a `#JAVA` note and `diagram.png`; Client2 posts a question | All boards showed all three items; the lightbox opened on Client3 (opened by direct method call). Clicking the stream link was not tested | PASS |

## 7. Labeled Screenshots

Captured during the test run above.

**Figure 1: Host Session & Active Connections** (the server has no console output)

![Host session](docs/screenshots/fig1-host-session.png)

**Figure 2: Multi-Client Chat Session & Active Peer List**

![Peer session](docs/screenshots/fig2-peer-session.png)

**Figure 3: Study Board (Synchronized Notes & Questions)**

The left panel in Figures 1 and 2 shows the same board items on the host and a peer.

**Figure 4: Session Media Sharing & Lightbox Preview**

![Image preview](docs/screenshots/fig4-image-preview.png)

**Figure 5: Error Prompt (Duplicate Nickname)**

![Duplicate nickname error](docs/screenshots/fig5-duplicate-nickname.png)

## 8. Pair Contribution Log

| Date | Contributor | Task / Feature Implemented | Commit Link / SHA |
|---|---|---|---|
| `[Date]` | Jade Vincent Suobiron Vallar | Server socket setup, thread-per-client handlers, and message parsing | `[Commit SHA]` |
| `[Date]` | Krisley Son U. Gumarang | Swing client layout, connection inputs, and peer list panel | `[Commit SHA]` |
| `[Date]` | Jade Vincent Suobiron Vallar | Study board synchronization (`ENTRY`, `REPLY`, `RESOLVE`) | `[Commit SHA]` |
| `[Date]` | Krisley Son U. Gumarang | Base64 media sharing and image lightbox preview | `[Commit SHA]` |
| `[Date]` | Jade Vincent Suobiron Vallar | Input validation, `/name` and `/exit` commands, and thread-safe locks | `[Commit SHA]` |
| `[Date]` | Krisley Son U. Gumarang | Integration testing (TC-01 to TC-08), bug fixes, and README | `[Commit SHA]` |

## Known Limitations

- Data is kept in memory and is lost when the session ends.
- There is no password or encryption, so use trusted networks only.
- There is no file size limit; very large files may be slow or fail.
- After `/name`, Connection Status still shows the old nickname.
- Clicking an image link in the live stream may not open the preview; double-click the image in the left list.
