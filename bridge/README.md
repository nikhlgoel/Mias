# Mias Bridge — Remote PC Offload & Control

> The "PC part" of Mias. Lets a user keep their PC running at home, leave for
> college/work, and from their phone (Android or iOS) **keep jobs running, send
> new instructions, and watch live updates — over any network, any distance,
> smoothly.** The PC side ships as an **IDE extension** (VS Code first; a
> dedicated IDE later). The phone connects with a **6-char code, a link, or a
> saved device**.

This folder is the home of the PC-version work. **It is research + design right
now — no production code yet.** Build order agreed with the team:

1. ✅ **Research & design the Bridge** (this folder).
2. ⏭️ Migrate the existing Android app Kotlin → **React Native** (Android first, tested).
3. ⏭️ Build the Bridge per the roadmap here (extension → relay → client).

## Read in this order

| Doc | What it answers |
|---|---|
| [docs/00-OVERVIEW.md](docs/00-OVERVIEW.md) | Problem, goals, non-goals, glossary, how it fits Mias today |
| [docs/01-RESEARCH-AND-OPTIONS.md](docs/01-RESEARCH-AND-OPTIONS.md) | Every connectivity option compared; your code/link idea evaluated; container-test results |
| [docs/02-ARCHITECTURE.md](docs/02-ARCHITECTURE.md) | The chosen architecture (ADR), components, transport tiers, data flow |
| [docs/03-PAIRING-AND-SECURITY.md](docs/03-PAIRING-AND-SECURITY.md) | Pairing UX (code/link/saved devices), SPAKE2+ E2EE, threat model |
| [docs/04-PROTOCOL-AND-LIVE-UPDATES.md](docs/04-PROTOCOL-AND-LIVE-UPDATES.md) | Wire protocol, sessions, streaming, resume, live updates, MCP reuse |
| [docs/05-REACT-NATIVE-MIGRATION.md](docs/05-REACT-NATIVE-MIGRATION.md) | What the RN/iOS move means for the Bridge, and vice-versa |
| [docs/06-ROADMAP-PHASES.md](docs/06-ROADMAP-PHASES.md) | Detailed phased plan: stages, deliverables, tests, exit criteria |
| [docs/07-THREAT-REVIEW-AND-FIXES.md](docs/07-THREAT-REVIEW-AND-FIXES.md) | Security/perf self-review: every flaw → fix → where applied + a build checklist |
| [docs/08-RESILIENCE-AND-QUALITY.md](docs/08-RESILIENCE-AND-QUALITY.md) | Deep per-connection resilience: multi-channel, fallback ladder, state machine, zero-quality-drop guarantee, network factors, call graph |
| [docs/09-PC-EXTENSION-CAPABILITIES.md](docs/09-PC-EXTENSION-CAPABILITIES.md) | The PC worker: models+APIs, skills engine, instructions, memory + self-learning, full feature catalog |
| [docs/10-UX-UI.md](docs/10-UX-UI.md) | UX/UI design system: eye-comfort theming, streaming/connection/pairing UX, accessibility, motion |
| [docs/11-MOBILE-APP-GAPS.md](docs/11-MOBILE-APP-GAPS.md) | Scan of the current Android app: what's missing for the whole flow (scanner, pairing crypto, streaming, connection UI…) + a plan tied to the RN migration |
| [poc/](poc/) | The runnable connectivity proof-of-concept + its results |

## One-paragraph summary of the decision

The PC **dials out** to a small **rendezvous relay** (exactly how VS Code Tunnels
and Cloudflare Tunnel work) so there is **never an inbound port, VPN, or
dynamic-DNS** to configure — this is what makes "any network, any distance"
actually work. The phone also dials out to the relay and is matched to the PC by
a short pairing code. Everything between phone and PC is **end-to-end encrypted**
with a key derived from the pairing code (SPAKE2+), so the relay is a
**zero-knowledge byte-forwarder** — it can never read prompts, code, or outputs.
This keeps Mias's "no third party ever sees your data" promise intact while
crossing the internet. A direct **P2P (WebRTC) upgrade** is layered on later to
cut the startup latency where the network allows it; the relay is always the
reliable fallback.
