# Restrict Automated Replies Exclusively to Declined Calls

Eliminate automated reply dispatching on incoming text and chat messages, restricting auto-reply responses solely to blocked/declined calls while ensuring incoming messages are cleanly silenced and dismissed without disturbing the user.

## User Review & Critical Decisions

> [!IMPORTANT]
> The user confirmed the following decisions during Phase 1 clarification:
> - **Auto-Reply Restriction**: Automated replies must only be sent for declined/blocked calls, not for incoming text or chat messages.
> - **Incoming Message Handling**: Regular incoming chat and SMS messages should be silenced and dismissed without sending any reply.

- **Confirmed Decision 1**: Remove automatic RemoteInput messaging reply behavior from incoming text/chat notifications.
- **Confirmed Decision 2**: Preserve auto-reply capability exclusively for Stage 1 / Stage 2 VoIP declined calls and blocked callers.
- **Confirmed Decision 3**: Maintain Smart Urgent Breakthrough (OTPs, 2FA, logistics) and Whitelist exemptions undisturbed so critical alerts still reach the user.

---

### 1. Overview & Core Concept

Sentinal protects user focus during Gaming, Deep Work, and Do Not Disturb sessions. Previously, Sentinal intercepted incoming notifications from messaging apps (e.g., WhatsApp, Telegram, Google Messages) and attempted to send an automated focus status reply via `RemoteInput` to text messages. This caused unwanted messages to be sent to anyone who texted the user.

This change will:
1. Completely remove automated reply dispatching on standard incoming chat/SMS notifications.
2. Ensure regular messages from messaging apps are quietly silenced and dismissed (zero-trace dismissal) without triggering replies.
3. Keep automated focus responses reserved strictly for calls that are actively declined or blocked.
4. Update UI labels and settings descriptions in Sentinal to clearly communicate that auto-replies apply to declined calls.

---

### 2. User Experience & Visual Design

- **Incoming Text Messages During Focus**:
  - A friend or colleague sends a WhatsApp or SMS message while Focus mode is active.
  - The notification is evaluated for whitelisting or urgent keywords (e.g., banking OTPs, delivery alerts).
  - If not urgent/whitelisted, the notification is cleanly silenced and dismissed.
  - **Zero automated messages are sent** back to the sender.
- **Incoming Calls During Focus**:
  - An incoming VoIP or cellular call arrives while Focus mode is active.
  - Sentinal checks Starred Contacts and 3-Minute Repeat Caller rules.
  - If rejected/declined, Sentinal dispatches the automated focus reply informing the caller that the user is focused, with instructions to reply 'URGENT' if critical.
- **Settings & Dashboard UI Polish**:
  - The Auto-Reply preference card label and summary in `SettingsScreen.kt` will be updated from generic auto-reply to "Auto-Reply on Declined Calls" with the subtitle "Notify callers when their call is rejected during focus".

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Complete Elimination of Chat-Message Auto-Replies vs. Optional Toggle**
  - *Chosen Approach*: Discontinue auto-replies on regular chat/SMS messages directly, keeping auto-replies strictly for declined calls as requested by the user.
  - *Why*: Auto-replying to every group chat or casual text message creates unnecessary noise, annoys contacts, and disrupts normal chat threads. Call rejection auto-replying is the primary high-value use case.
  - *Alternatives Considered*: Adding an extra toggle setting. Rejected because the user explicitly stated it should not be doing this, and streamlining reduces configuration fatigue.
- **Decision 2: RemoteInput Session Caching for VoIP Follow-Up**
  - *Chosen Approach*: Retain `remoteInputSessions` caching for pre-warming VoIP reply channels, but eliminate the active trigger on incoming chat notifications.
  - *Why*: Allows Stage 1 & Stage 2 VoIP call rejection auto-replies to continue functioning smoothly when a VoIP call is blocked.

---

### 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────┐
│             NotificationGuardService                   │
└────────────────────────────────────────────────────────┘
                           │
                           ▼
          Is Outgoing Call? ───► YES ──► Record & Bypass (No Reply)
                           │ NO
                           ▼
          Is Incoming Call? ───► YES ──► Check Repeat/Starred
                           │              │
                           │              ▼
                           │             Decline & Send Call Auto-Reply
                           │
                           ▼ NO (Regular Notification)
          Is Urgent / Whitelisted? ──► YES ──► Allow through
                           │ NO
                           ▼
             Silence & Dismiss Cleanly (ZERO Auto-Reply)
```

- **Core Code Changes**:
  - `NotificationGuardService.kt`:
    - Remove Section C auto-reply execution on standard messaging notifications (`attemptRemoteInputAutoReply` call in regular notification flow).
    - Ensure regular notifications flow directly to Zero-Trace dismissal without sending messages.
  - `SettingsScreen.kt` & `strings.xml`:
    - Update user-facing copy to clarify that auto-replies apply to declined calls.
  - `ExampleRobolectricTest.kt`:
    - Add unit tests verifying incoming chat notifications do not generate auto-replies, while call rejection paths retain auto-reply capability.
