# 10 — UX / UI Design System

UI is not decoration here — people run **long sessions**, often tired, often on a
phone in poor light. The bar: **calm, beautiful, easy on the eyes, and reassuring
when the network wobbles.** This is the design contract for both the mobile app
(post-RN) and the IDE extension panel.

## 1. Principles
1. **Content first, chrome quiet.** The conversation/output is the hero; controls
   recede until needed.
2. **Calm under failure.** Network problems are communicated *reassuringly*, never
   alarmingly — because the work isn't actually lost (08 section 1).
3. **Eye-comfort by default.** Long-session legibility beats flashy contrast.
4. **Consistent + predictable.** One token system, one motion language everywhere.
5. **Accessible = better for everyone.** Dynamic type, contrast, reduce-motion,
   color-blind-safe status.
6. **Fast & smooth.** 60fps, no layout jank, instant optimistic feedback.

## 2. Theming & eye comfort (the long-session core)
- **Modes:** System / Light / Dark / **True-black (AMOLED)** / **Warm-night**
  (blue-light reduction, optional schedule / sunset-linked).
- **Never pure values:** avoid `#000` text on `#FFF` and pure-white on pure-black
  (halation/eye strain). Use **off-white (~#F5F5F4) and near-black (~#0E0F12)** as
  the extreme ends; body text at ~85–90% contrast, not 100%.
- **Contrast:** WCAG **AA minimum, AAA for body text** where feasible.
- **Color tokens (semantic, not raw):** `bg/{base,raised,sunken}`,
  `text/{primary,secondary,muted}`, `accent`, `success/warn/danger/info`,
  `connection/{p2p,relay,tailscale,degraded,offline}`. Light & dark values per
  token. Reuse/extend the app's existing **glass** aesthetic (`CognitionGlow`)
  as a restrained accent, not everywhere.
- **Elevation:** soft, low-contrast shadows in light; subtle borders + slightly
  raised bg in dark (shadows read poorly on dark).

## 3. Typography & spacing
- **Type:** a humanist sans (Inter / SF / Roboto Flex) for UI; **monospace with
  syntax highlighting** for code; comfortable **line-height ~1.5** for chat.
- **Scale:** a fixed modular scale (e.g., 12/14/16/20/24/32) mapped to tokens;
  **respect OS Dynamic Type / font-size** — never hard-pin px for body text.
- **Spacing/radius:** 4px base grid; generous touch padding (≥44pt targets);
  consistent corner radius token (e.g., 12–16 for cards, full for chips).

## 4. Streaming & chat UX
- **Smooth token rendering** via the jitter buffer (08 section 6): steady "typing", never
  a stutter-then-dump, even after a reconnect.
- **Cognition states** (Thinking/Acting/Waiting) shown subtly (a soft pulse on the
  orb/indicator), with **collapsible ReAct steps** for the curious, hidden by
  default.
- **Markdown + code blocks** with copy buttons, language labels, soft wrap toggle.
- **Optimistic send:** user message appears instantly as *pending → sent →
  answered*; failures are recoverable, never silently dropped.
- **Message affordances:** long-press copy/share/quote, jump-to-latest, regen,
  edit-and-resend.

## 5. Connection-status UX (signature of this product)
- **Always-present, unobtrusive chip** showing state + path:
  `● Direct` (P2P) / `● Relay` / `● Tailscale` / `◐ Degraded` / `○ Offline`,
  with optional latency (e.g., `Relay · 80ms`). Color **and** icon/shape (not
  color alone) for color-blind safety.
- **Reassuring reconnect:** a calm inline banner — *"Reconnecting… your work is
  still running on your PC."* On recovery, a brief *"Caught up"* toast. **Never** a
  scary red modal; the output is intact.
- **Offline mode:** queued instructions are visible with a small "will send when
  back online" note; nothing feels lost.
- **Degraded:** a soft hint ("slow network — buffering for smoothness"), not an
  error.
- **Approval / escalation states (07-U2).** Every protocol approval round-trip
  needs a phone screen, or it's an unreachable state:
  - **Scope-escalation requested** — the session is `read`, the user asks for an
    `edit`/`exec` action: show *"This needs Edit access on your PC — request it?"*
    → pending → granted/denied. Granting is **biometric-confirmed** (07-S26/S27).
  - **Per-op approval pending** — an `exec`/destructive op is waiting on the
    per-op biometric confirm (and allowlist check); show exactly what will run.
  - **Approval denied / timed out** — a calm, recoverable result, never a dead end.
  - **"Needs your input"** (`notify`) — a deep-link from the push that lands on the
    exact prompt awaiting an answer.

## 6. Pairing & devices UX (PC shows, phone scans)
**PC extension panel (displays):** a large, scannable **QR** (ECC-Q, ≥220px, quiet
zone), the **code** below it (grouped, high-contrast, `K7Q · 9F2H`) as the
fallback, a **live countdown** ring, and on scan an **"Approve <phone>?"**
confirmation. This is the *only* place a QR is shown.

**Phone "Connect" screen (scans):** a **camera viewfinder** with an alignment
frame, torch toggle, and haptic+check on decode — **not** a QR to be photographed.
Always-present **"enter code manually"** fallback; graceful degrade if camera is
denied. Explainer: *"End-to-end encrypted · expires in 4:58."*

**Saved devices (phone):** cards with name, last-connected, online dot, **one-tap
reconnect**; swipe to **forget/revoke** (confirm). A clear "this device can run
commands on your PC" scope indicator.

## 7. PC control surfaces (phone + extension)
- **PC vitals card:** CPU/GPU/VRAM/temp sparkline, loaded model, queue depth.
- **Live panels:** build/test results (pass/fail + tail), file-change feed, job
  list with **pause/cancel**.
- **Skill runner:** searchable skill list; running a skill shows streamed output;
  `exec`/destructive skills show an **approval prompt** with exactly what will run.
- **Model/provider switcher** with a "why this model" explainer.

## 8. Motion & micro-interactions
- **Purposeful, short** (120–240ms), eased; a shared motion token set.
- **Respect `prefers-reduced-motion`** → cross-fades instead of movement.
- **Haptics** (mobile) on key events: connected, message done, approval needed.
- **Skeletons** for loading; never spinners-on-blank for content we can outline.

## 9. Accessibility
- Full screen-reader labels (incl. connection state announced politely on change).
- Dynamic type to large sizes without breaking layout.
- Color-blind-safe status (icon+label+shape).
- Large hit targets, focus rings, full keyboard nav in the extension webview.
- Captions/labels for the voice and vision flows.

## 10. IDE extension panel
- **Themed to the IDE** (consumes VS Code light/dark tokens) so it feels native.
- A **status-bar item** for session state (started / paired / streaming).
- Minimal, information-dense panel: session + code/QR, connected devices, capability
  scope toggle (read/edit/exec), live job/build feed, skill runner.
- Unobtrusive; never steals focus from the editor.

## 11. Implementation notes (post-RN)
- Ship the **token system as a TS theme package** shared by app screens; map to RN
  (e.g., a styled system / Tailwind-RN / Restyle) so light/dark/warm switch is one
  source of truth.
- Keep the existing visual identity (orb, glass glow) but systematize it; don't
  scatter ad-hoc colors.
- Build a small **component library** (Chip, Card, Banner, CodeBlock, StatusDot,
  CountdownRing, DeviceCard, VitalsSparkline) reused across app + (where possible)
  the extension webview.
