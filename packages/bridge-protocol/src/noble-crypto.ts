/**
 * Pure-JavaScript CryptoProvider via the audited @noble libraries.
 *
 * The key property: @noble is pure JS with zero native deps, so this ONE
 * provider runs identically on the PC (Node) AND the phone (React Native /
 * Hermes). That resolves the "RN has no JS crypto" gate — the phone can run the
 * exact SecureChannel/BridgePeer the session server runs, with no native crypto
 * module. It is RN-safe and is the default provider everywhere.
 *
 * (`node-crypto.ts` remains for Node consumers that prefer the platform's crypto;
 * the two interoperate — proven by the node↔noble interop test.)
 */
import { x25519 } from '@noble/curves/ed25519.js';
import { hkdf } from '@noble/hashes/hkdf.js';
import { hmac } from '@noble/hashes/hmac.js';
import { sha256 } from '@noble/hashes/sha2.js';
import { gcm } from '@noble/ciphers/aes.js';
import { randomBytes as nobleRandomBytes } from '@noble/hashes/utils.js';
import type { CryptoProvider, KeyPair } from './crypto.ts';

export const nobleCryptoProvider: CryptoProvider = {
  randomBytes(n: number): Uint8Array {
    return nobleRandomBytes(n);
  },

  x25519Generate(): KeyPair {
    const privateKey = x25519.utils.randomSecretKey();
    return { privateKey, publicKey: x25519.getPublicKey(privateKey) };
  },

  x25519Shared(privateKey: Uint8Array, peerPublicKey: Uint8Array): Uint8Array {
    return x25519.getSharedSecret(privateKey, peerPublicKey);
  },

  hkdf(ikm: Uint8Array, salt: Uint8Array, info: Uint8Array, length: number): Uint8Array {
    return hkdf(sha256, ikm, salt, info, length);
  },

  hmac(key: Uint8Array, data: Uint8Array): Uint8Array {
    return hmac(sha256, key, data);
  },

  aesGcmSeal(key: Uint8Array, nonce: Uint8Array, plaintext: Uint8Array, aad?: Uint8Array): Uint8Array {
    // @noble gcm returns ciphertext||tag — the same layout as node:crypto here.
    return gcm(key, nonce, aad).encrypt(plaintext);
  },

  aesGcmOpen(key: Uint8Array, nonce: Uint8Array, ciphertext: Uint8Array, aad?: Uint8Array): Uint8Array {
    return gcm(key, nonce, aad).decrypt(ciphertext);
  },
};
