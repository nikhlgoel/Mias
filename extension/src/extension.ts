/**
 * Mias Bridge — VS Code extension (host side of the Bridge, bridge/docs/02/03).
 *
 * Architecture: the extension owns the editor UI; the E2EE session server runs
 * as a **sidecar** child process (02 open-question 3 — a sidecar survives IDE
 * reloads, and keeps `node:crypto`/`ws` out of the extension host). On "Start
 * Session" the extension mints the pairing material and shows a webview with the
 * code + verified-link/QR the phone scans; the sidecar dials the relay and pairs.
 *
 * This is the P1 scaffold: the panel + pairing material + sidecar lifecycle are
 * real; the QR image render and the sidecar's production bundling are the
 * remaining polish (tracked in bridge/docs/06 P3/P6).
 */
import * as vscode from 'vscode';
import { spawn, type ChildProcess } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import * as path from 'node:path';

let sidecar: ChildProcess | null = null;
let panel: vscode.WebviewPanel | null = null;

const CROCKFORD = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'; // ambiguity-free (bridge/docs/03 section 1a)

/** ≥30-bit human pairing code (7 Crockford Base32 chars). */
function makePairingCode(): string {
  const bytes = randomBytes(7);
  let out = '';
  for (let i = 0; i < 7; i++) out += CROCKFORD[bytes[i]! % 32];
  return out;
}

/** High-entropy rendezvous id (relay routing key; never the code — 07-S1). */
function makeRendezvousId(): string {
  return randomBytes(16).toString('base64url');
}

export function activate(context: vscode.ExtensionContext): void {
  context.subscriptions.push(
    vscode.commands.registerCommand('mias.startSession', () => startSession(context)),
    vscode.commands.registerCommand('mias.stopSession', () => stopSession()),
  );
  context.subscriptions.push({ dispose: () => stopSession() });
}

function startSession(context: vscode.ExtensionContext): void {
  stopSession();
  const config = vscode.workspace.getConfiguration('mias');
  const relayUrl = config.get<string>('relayUrl', 'wss://relay.mias.local');
  const workerUrl = config.get<string>('workerUrl', 'http://127.0.0.1:8401/rpc');

  const code = makePairingCode();
  const rendezvousId = makeRendezvousId();
  // Advisory link/QR payload (bridge/docs/03 section 1c). The app validates the
  // relay host against its allowlist; E2EE+PAKE protect content regardless.
  const exp = Math.floor(Date.now() / 1000) + 5 * 60;
  const relayHost = relayUrl.replace(/^wss?:\/\//, '');
  const pairingLink =
    `https://pair.mias.app/v1#mias1:${rendezvousId}.${b64url(code)}.${b64url(relayHost)}.${exp}`;

  sidecar = spawnSidecar(context, { relayUrl, rendezvousId, code, workerUrl });
  showPairingPanel(context, code, pairingLink);
}

function spawnSidecar(
  context: vscode.ExtensionContext,
  env: { relayUrl: string; rendezvousId: string; code: string; workerUrl: string },
): ChildProcess {
  // Production packaging bundles the session server to JS; here we point at the
  // repo sibling for development.
  const bin = path.join(context.extensionPath, '..', 'session-server', 'src', 'bin.ts');
  const child = spawn(process.execPath, [bin], {
    env: {
      ...process.env,
      RELAY_URL: env.relayUrl,
      RENDEZVOUS_ID: env.rendezvousId,
      PAIRING_CODE: env.code,
      MCP_URL: env.workerUrl,
    },
    stdio: 'pipe',
  });
  const out = vscode.window.createOutputChannel('Mias Bridge');
  child.stdout?.on('data', d => out.append(String(d)));
  child.stderr?.on('data', d => out.append(String(d)));
  child.on('exit', codeNum => out.appendLine(`[session-server] exited (${codeNum ?? 'signal'})`));
  return child;
}

function showPairingPanel(context: vscode.ExtensionContext, code: string, link: string): void {
  panel = vscode.window.createWebviewPanel('miasPairing', 'Mias — Pair your phone', vscode.ViewColumn.Beside, {
    enableScripts: false,
  });
  const grouped = `${code.slice(0, 3)} · ${code.slice(3)}`;
  panel.webview.html = `<!doctype html><html><head><meta charset="utf-8">
    <style>
      body { font-family: var(--vscode-font-family); text-align: center; padding: 32px; }
      .code { font-size: 40px; letter-spacing: 4px; font-weight: 600; margin: 16px 0; }
      .link { font-size: 12px; opacity: .7; word-break: break-all; margin-top: 20px; }
      .hint { opacity: .8; }
    </style></head><body>
    <h2>Pair your phone</h2>
    <p class="hint">Open Mias on your phone → Connect → scan or enter this code.</p>
    <div class="code">${grouped}</div>
    <p class="hint">End-to-end encrypted · expires in 5:00</p>
    <p class="link">${escapeHtml(link)}</p>
    <p class="hint" style="margin-top:24px;font-size:12px">
      (QR image render — bridge/docs/03 section 1c — is the remaining pairing-panel polish.)
    </p>
    </body></html>`;
  panel.onDidDispose(() => { panel = null; stopSession(); }, null, context.subscriptions);
}

function stopSession(): void {
  sidecar?.kill();
  sidecar = null;
  panel?.dispose();
  panel = null;
}

export function deactivate(): void {
  stopSession();
}

function b64url(s: string): string {
  return Buffer.from(s, 'utf8').toString('base64url');
}
function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]!));
}
