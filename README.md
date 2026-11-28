StudyHive — A Cozy Place for Studyholics

**ITAS1 Final Project Documentation** · Java LAN Peer Study Chat & Collaboration Application

StudyHive is a multi-client Java Swing LAN chat and collaborative study desktop application built on TCP sockets (`StudyServer` & `StudyClient`). Tailored for classmates reviewing subjects over a local network, it structures peer discussions with color-coded Q&A resolution, tagged study notes, live keyword filtering, media lightbox previews, and synchronized session timers.

## Table of Contents

1. [Project Overview, Team & Repository Metadata](#1-project-overview-team--repository-metadata)
2. [Concept Proposal & Required Features](#2-concept-proposal--required-features)
3. [Architecture Diagram & Message Protocol](#3-architecture-diagram--message-protocol)
4. [Application Screenshots](#4-application-screenshots)
5. [Setup, Execution & Troubleshooting](#5-setup-execution--troubleshooting)
6. [Verification & Minimum Test Results](#6-verification--minimum-test-results)
7. [Pair Contribution Log & Attribution](#7-pair-contribution-log--attribution)

---

## 1. Project Overview, Team & Repository Metadata

| | |
| --- | --- |
| **Course & Repository** | ITAS1 — Network Programming · Section: [BSIT 2B-SD] · Repo: `ITAS1-[BSIT 2B-SD]-Vallar-Gumarang` |
| **Team Members & GitHub** | **Partner 1:** Jade Vincent Suobiron Vallar (jadevincentvallar) — Lead Architect, Server/Client & Sync<br>**Partner 2:** Krisley Son U. Gumarang (leymonq) — Vintage Swing UI, Controls & LAN Testing |
| **Theme & Approval** | Study Circle / Library Study Hub · Concept approved: [November 27, 2026] by [Instr. Kenth Joshua Espina] |
| **Tech Stack** | Java SE (JDK 17+), Java Swing GUI, TCP `ServerSocket`/`Socket`, Multithreading, Apache NetBeans |

## 2. Concept Proposal & Required Features

**User problem & audience:** Students reviewing in groups over campus Wi-Fi or local LAN frequently lose key questions, answers, and study files inside fast-moving ordinary text chats.

**Distinct design:** Warm cream-brown vintage Swing UI featuring animal avatars + custom photo upload (+), Host/Join session loading dialogs, live Session/Joined timers, center-aligned activity notices, and a 3-column workspace.

**Concurrent TCP architecture:** `StudyServer` spawns a `ClientHandler` thread per client (supporting 3+ concurrent LAN peers) with synchronized broadcasts; `StudyClient` updates Swing via `SwingUtilities.invokeLater()` without blocking the EDT.

**Identity, commands & validation:** Enforces unique, non-blank nicknames; supports `/name <newname>`, `/exit`, and `/help`; rejects blank messages and invalid IP/port inputs gracefully.

### Two Original Synchronized Theme Features

**1. Visual Study Board (Notes & Live Q&A Resolution)**
Users tag posts as `[NOTE]` (warm yellow highlight) or `[QUESTION]` (solid red while OPEN). Any peer can select a question to post threaded replies ("Respond to Question") or mark it **RIGHT / RESOLVED**, turning it green across all connected clients.

**2. Live Entry Filter, Activity Log & Media Sharing**
Real-time keyword search and category filtering (All Entries, Notes, Questions), timestamped Activity Log auditing, and TCP file/image broadcasting with dimmed Lightbox preview and local saving.

## 3. Architecture Diagram & Message Protocol

```text
StudyClient #1 (Host GUI)  <--- TCP Socket (Port 5000) --->  +------------------------------------+
StudyClient #2 (Peer GUI)  <--- TCP Socket (Port 5000) --->  | StudyServer (ServerSocket + Thread |
StudyClient #3 (Peer GUI)  <--- TCP Socket (Port 5000) --->  | per Client, Synchronized State)    |
                                                             +------------------------------------+
```

| Frame Type | Pipe-Delimited Wire Format (Base64 Payloads) | Synchronized Action |
| --- | --- | --- |
| Handshake & Roster | `JOIN\|nickB64\|avatarB64` / `WELCOME\|nickB64\|startMs` / `PEERS\|list` | Validates unique nickname; syncs peer list & timers. |
| Chat & Commands | `CHAT\|sender\|avatar\|time\|msg` / `CMD\|/name\|/exit\|/help` | Broadcasts chat once per client; processes commands. |
| Notices & Study Board | `SYS\|noticeB64` / `ENTRY\|id\|NOTE\|QUESTION\|author\|text\|time\|res` | Posts centered notices and yellow Note / red Question. |
| Q&A Reply & Resolve | `REPLY\|entryId\|author\|reply\|time` / `RESOLVE\|entryId\|true` | Appends reply; switches Question card red → green. |
| Media / File Transfer | `ENTRY\|id\|IMAGE\|FILE\|author\|caption\|fileName\|payloadB64\|time` | Broadcasts attachment; enables Lightbox & local save. |

## 4. Application Screenshots

Live LAN session evidence captured across Host (`jade`) and Peer (`krisley`) connected on `192.168.43.36:5000`.

![StudyHive Lobby & Profile Setup](screenshots/01-lobby.png)

*Figure 1: StudyHive Lobby & Profile Setup — Custom avatar/nickname profile ('ley'), detected Device IP (192.168.43.36), and Join/Create Session controls.*

| ![Host Session View](screenshots/02-host-session.png) | ![Peer Session View](screenshots/03-peer-session.png) |
| :---: | :---: |
| *Figure 2: Host Session View (jade) — Shows OPEN (red) Question transitioning to RESOLVED (green), yellow [NOTE] post, centered notices, and Host Session Time.* | *Figure 3: Peer Session View (krisley) — Synchronized Live Study Stream, filtered Q&A sidebar, Peer Session/Joined Timers, and Online Study Peers.* |

## 5. Setup, Execution & Troubleshooting

**Dependencies:** JDK 17+ and Apache NetBeans IDE (or `javac`/`java` CLI). Uses standard Java SE libraries only (no external JARs).

### Running the App

1. **Start Host & Server:** Run `com.mycompany.studyhive.Main` → select an avatar (or upload via **+**), enter a unique nickname, click **Save** → click **Create a Session** to bind `ServerSocket` on Port 5000.
2. **Connect Peers (3+ instances):** On peer computers on the same LAN/Wi-Fi, run `Main` → enter a unique nickname → enter Host IPv4 (e.g., `192.168.43.36`) and Port `5000` → click **Join Session**.

### Troubleshooting

- Ensure all machines share the same subnet.
- Verify the Host IP via `ipconfig`.
- Allow Java/TCP Port 5000 through Windows Firewall if a connection times out.

## 6. Verification & Minimum Test Results

| ID | Test Case | Steps, Expected & Actual Result | Status |
| --- | --- | --- | --- |
| **TC-01** | 3 Concurrent LAN Clients | Host + 2 peers connect over LAN → All 3 stay connected; Peers Connected & timers update live. | ✅ PASS |
| **TC-02** | Group Broadcast & Notices | Send message & join/leave → Displayed once per client with sender/time and centered notices. | ✅ PASS |
| **TC-03** | Nickname & Slash Commands | Test blank/duplicate names, `/name`, `/help`, `/exit` → Invalid names blocked; commands sync globally. | ✅ PASS |
| **TC-04** | Input & Network Errors | Send empty text; connect to invalid IP/unavailable server → Rejected cleanly; UI stays responsive. | ✅ PASS |
| **TC-05** | Abrupt Disconnection | Force-close one client window → Server catches disconnect, updates peer list, and continues. | ✅ PASS |
| **TC-06** | Original Feature 1: Q&A | Post `[QUESTION]` (red), reply, mark RESOLVED (green) → State and replies sync across all peers. | ✅ PASS |
| **TC-07** | Original Feature 2: Notes | Post `[NOTE]` (yellow) & search/filter entries → Live filter and Activity Log update across peers. | ✅ PASS |
| **TC-08** | Media Lightbox & Save | Share image/doc, click preview, click Save Selected Media / File → Opens preview & saves locally. | ✅ PASS |

## 7. Pair Contribution Log & Attribution

| Date | Member | Task Description | Commit Link |
| --- | --- | --- | --- |
| 2026-11-18 | Jade Vincent S. Vallar | Built `StudyServer`, `StudyClient`, `StudyModels` wire protocol, timers, and Q&A/media sync. | [commit-hash-1] |
| 2026-11-22 | Krisley Son U. Gumarang| Designed cream-brown vintage Swing UI, animal/custom avatars, Lightbox, and loading pop-ups. | [commit-hash-2] |
| 2026-11-27 | Both Members | Executed 3-client LAN test cases (TC-01 to TC-08), captured screenshots, and finalized README. | [commit-hash-3] |

### Citations & AI Disclosure

Built with Java SE (`java.net`, `java.io`, `javax.swing`, `java.awt`) in Apache NetBeans. Original architecture, UI design, and assets by the pair; AI assistance was used only for formatting documentation and structuring test tables.
