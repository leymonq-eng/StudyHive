StudyHive: Collaborative Networked Study Space

A Java desktop LAN chat app for study groups, with a shared study board and file sharing.

Project information	Details
Course	ITAS1 — Network Programming, Section BSIT 2B-SD
Theme	Collaborative study space
Members	Jade Vincent Suobiron Vallar, Krisley Son U. Gumarang
GitHub usernames	[Jade's username], leymonq-eng
Repository	ITAS1-BSIT-2B-SD-Vallar-Gumarang
Submission date	November 27, 2026
Commit hash	[Final commit hash]
Table of Contents
Project Overview
Concept Proposal
User Problem and Theme
Core Chat Features
Original Features
Architecture
Message Format
Commands
Setup and LAN Instructions
Screenshots
Testing Checklist
Division of Responsibilities
Contribution Log
Credits and AI Disclosure
Project Structure
1. Project Overview

StudyHive is a Java desktop app for group study over a local network. One member hosts a session and others join to chat, share notes and files, and work through questions together.

Java TCP sockets
Java Swing interface
One server thread per connected client
Shared study board held in memory
No database or external libraries
2. Concept Proposal
Item	Details
Target users	Students in study groups
User problem	Chat, notes, questions, and files get scattered or buried
Proposed solution	One app with a live chat stream and an organized study board
Original feature 1	Tagged Study Board (Notes and Questions)
Original feature 2	Session Media and File Sharing
Approval	[Instructor approval and date]
3. User Problem and Theme

During virtual study sessions, students often switch between apps to chat, share notes, and send files. In a single chat stream, important questions are easily lost.

StudyHive keeps these in one place with a cozy cream-and-coffee vintage theme: "A cozy place for studyholics."

4. Core Chat Features
Host or join using a configurable IP address and port (default 5000)
Unique nicknames, checked without regard to letter case
Messages broadcast to all connected members
Online peer list
Join, leave, and rename notices
Animal avatar selection or a custom picture
Session and joined-time timers
Activity log
/name, /exit, and /help commands
Blank-input and port-number checks, with error dialogs
Background networking threads and socket cleanup
5. Original Features
Feature 1: Tagged Study Board
Post a message tagged as Note or Question
Notes are highlighted; open questions are red and resolved ones green
Anyone can reply to a question, resolve it, or reopen it
Search by keyword and filter by category
Members who join late receive the existing board
Feature 2: Session Media and File Sharing
Share images and documents with everyone in the session
Preview images in a dimmed lightbox by double-clicking them in the board
Save shared files and images to your computer
6. Architecture
text
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
Components and thread safety
StudyServer: Binds a ServerSocket and starts a ClientHandler thread for each connection.
StudyClient: Manages one TCP socket and a background reader thread.
Broadcasts: The server locks the client list while broadcasting, and each handler's send method is synchronized.
Swing safety: StudyHiveUI uses SwingUtilities.invokeLater() for incoming network events.

Message path: A client sends an action over TCP, the server processes it and updates the board, then broadcasts the result. Each client updates its screen on the Swing Event Dispatch Thread. The host app runs the server and also connects to it as a client.

7. Message Format

Each message is one line with fields separated by pipes (|). Text fields are UTF-8 Base64 encoded, so they cannot break the format. IDs, entry types, numbers, and true/false values are sent as is.

Client to server
Command	Frame Structure	Description
JOIN	JOIN|<nickname>|<avatar>	Requests to join the session
CHAT	CHAT|<message>	Sends a chat message
CMD	CMD|<command_line>	Sends /name or /exit
NOTE	NOTE|<text>	Posts a note
QUESTION	QUESTION|<text>	Posts a question
MEDIA	MEDIA|<IMAGE or FILE>|<filename>|<size>|<base64_data>	Shares an image or file
REPLY	REPLY|<entry_id>|<text>	Replies to a question
RESOLVE	RESOLVE|<entry_id>|<true or false>	Resolves or reopens a question
Server to client
Command	Frame Structure	Description
WELCOME	WELCOME|<nickname>|<elapsed_seconds>	Accepts the join request
ERROR	ERROR|<message>	Rejects the join request
PEERS	PEERS|<nick1>,<nick2>,...	Updates the online peer list
CHAT	CHAT|<nickname>|<avatar>|<time>|<message>	Broadcasts a chat message
ENTRY	ENTRY|<id>|<type>|<author>|<content>|<filename>|<base64_data>|<time>|<resolved>	Adds an item to the board
REPLY	REPLY|<entry_id>|<author>|<text>|<time>	Adds a reply to a question
RESOLVE	RESOLVE|<entry_id>|<true or false>	Updates a question's status
SYS	SYS|<category>|<message>	Sends a system notice
8. Commands
Command	Action
/name <newname>	Change your nickname
/exit	Leave the session
/help	Show the command list (same as the Help button)

Notes, questions, replies, and file sharing are done through the interface.

9. Setup and LAN Instructions
Requirements
Item	Requirement
Java	JDK 17 or higher
Tools	Apache NetBeans or the command line
Libraries	None
Network	Same LAN or Wi-Fi
A. Compile

From the project folder:

bash
javac -encoding utf-8 -d out src/main/java/com/mycompany/studyhive/*.java
B. Run
bash
java -cp out com.mycompany.studyhive.Main

To include custom avatars or a logo, use java -cp "out;src/main/resources" com.mycompany.studyhive.Main on Windows (use : instead of ; on macOS or Linux).

In NetBeans, set the main class to com.mycompany.studyhive.Main, then Clean and Build and Run.

C. Host and join
Host: Enter a nickname, check Default Port, and click Create a Session.
Peers: Run the app, enter a unique nickname, the host's IP address, and the port, then click Join Session.
D. Find your IPv4 address (Windows)
Open Command Prompt and run ipconfig.
Find the IPv4 Address of your Wi-Fi or Ethernet adapter.
If Windows Firewall asks, allow Java on private networks.
Troubleshooting
Problem	What to check
Connection refused	The host created the session first; IP and port are correct
Cannot connect over LAN	Both computers are on the same network
Connection times out	The host's firewall allows Java
Nickname already in use	Choose a different nickname
Address already in use	Pick another port, such as 5001
Main class not found	Use exactly com.mycompany.studyhive.Main
10. Screenshots

Captured from a test run on one computer.

Figure 1: Host Session & Active Connections

Host session

Figure 2: Multi-Client Chat Session, Peer List & Study Board

Peer session

Figure 3: Session Media Sharing & Lightbox Preview

Image preview

Figure 4: Duplicate Nickname Error Prompt

Duplicate nickname error

11. Testing Checklist

Tested November 27, 2026 with a script that ran several app windows on one Linux machine and called the app's actions directly.

#	Test	Steps	Expected result	Actual result	Status
1	Concurrent clients	Connect a host and two peers	All connect	All three connected	Pass
2	Message broadcast	Host sends a message	Peers receive it	Both peers received it	Pass
3	Online list	Third user joins	All lists update	All showed three users	Pass
4	Duplicate nickname	Join with a used name	Rejected	Error dialog shown	Pass
5	Invalid port	Enter abc as port	Blocked with a message	Invalid Port dialog shown	Pass
6	Invalid IP	Enter 999.999.0.1	Blocked before connecting	Not checked first; connection failed with a dialog	Partial
7	Server offline	Join with no host	Error, no crash	Connection refused dialog	Pass
8	Port in use	Host on a busy port	Error shown	Address already in use dialog	Pass
9	Exit command	Peer sends /exit	Peer removed	Removed from host list	Pass
10	Abrupt disconnect	Close a peer socket	Peer removed	Removed from host list	Pass
11	Board and image sync	Post note, question, image	All boards update	All showed three items; preview opened by direct method call	Pass
12	Rename	Use /name	All lists update		Not tested
13	Reply and resolve	Reply, then resolve	Synced; turns green		Not tested
14	LAN connection	Join from a second computer	Connects		Not tested
12. Division of Responsibilities
Member	Assigned responsibilities	Actual contributions
Jade Vincent Suobiron Vallar	Server, study board synchronization, commands and validation	[To be filled in]
Krisley Son U. Gumarang	Client interface, media sharing, testing and README	[To be filled in]
13. Contribution Log
Member	Date	Task	Commit link
Jade Vincent Suobiron Vallar	[Date]	Server socket setup, thread-per-client handlers, and message parsing	[Commit link]
Krisley Son U. Gumarang	[Date]	Swing client layout, connection inputs, and peer list panel	[Commit link]
Jade Vincent Suobiron Vallar	[Date]	Study board synchronization (ENTRY, REPLY, RESOLVE)	[Commit link]
Krisley Son U. Gumarang	[Date]	Base64 media sharing and image lightbox preview	[Commit link]
Jade Vincent Suobiron Vallar	[Date]	Input validation, /name and /exit commands, and thread-safe locks	[Commit link]
Krisley Son U. Gumarang	[Date]	Integration testing (TC-01 to TC-08), bug fixes, and README	[Commit link]
14. Credits and AI Disclosure
Built with Java and Swing
Networking uses java.net
No external libraries
AI assistance: Claude was used to separate the source files from a PDF, fix compile, formatting, and server startup errors, change the Help button label, run scripted tests, capture screenshots, and draft this README. [Add any other AI use, including how the original code was written.]
15. Project Structure
text
StudyHive/
├── README.md
├── pom.xml
├── docs/
│   └── screenshots/
└── src/
    └── main/
        ├── java/com/mycompany/studyhive/
        │   ├── Main.java
        │   ├── StudyModels.java
        │   ├── StudyServer.java
        │   ├── StudyClient.java
        │   └── StudyHiveUI.java
        └── resources/          # Optional avatars/ and logo/
Known Limitations
Data is kept in memory and is lost when the session ends.
There is no password or encryption, so use trusted networks only.
There is no file size limit; very large files may be slow or fail.
After /name, Connection Status still shows the old nickname.
Clicking an image link in the live stream may not open the preview; double-click the image in the board instead.
