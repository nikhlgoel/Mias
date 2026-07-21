/**
 * Node crypto provider for the SecureChannel — used by the relay, the session
 * server, and tests (the PC side of the Bridge). NOT imported by the React
 * Native app (which gets a native crypto provider), so `node:crypto` never
 * reaches a Metro bundle.
 */
import {
  createHmac,
  createCipheriv,
  createDecipheriv,
  generateKeyPairSync,
  diffieHellman,
  createPublicKey,
  createPrivateKey,
  hkdfSync,
  randomBytes as nodeRandomBytes,
} from 'node:crypto';
import type { CryptoProvider, KeyPair } from './crypto.ts';

// X25519 raw-key <-> Node KeyObject helpers: wrap raw 32-byte keys in the
// minimal SPKI/PKCS8 headers Node expects for DER import/export.
const SPKI_PREFIX = Buffer.from('302a300506032b656e032100', 'hex');
const PKCS8_PREFIX = Buffer.from('302e020100300506032b656e04220420', 'hex');

function rawToPublicKey(raw: Uint8Array) {
  return createPublicKey({ key: Buffer.concat([SPKI_PREFIX, Buffer.from(raw)]), format: 'der', type: 'spki' });
}
function rawToPrivateKey(raw: Uint8Array) {
  return createPrivateKey({ key: Buffer.concat([PKCS8_PREFIX, Buffer.from(raw)]), format: 'der', type: 'pkcs8' });
}
function publicKeyToRaw(key: ReturnType<typeof createPublicKey>): Uint8Array {
  const der = key.export({ format: 'der', type: 'spki' }) as Buffer;
  return new Uint8Array(der.subarray(der.length - 32));
}
function privateKeyToRaw(key: ReturnType<typeof createPrivateKey>): Uint8Array {
  const der = key.export({ format: 'der', type: 'pkcs8' }) as Buffer;
  return new Uint8Array(der.subarray(der.length - 32));
}

export const nodeCryptoProvider: CryptoProvider = {
  randomBytes(n: number): Uint8Array {
    return new Uint8Array(nodeRandomBytes(n));
  },

  x25519Generate(): KeyPair {
    const { publicKey, privateKey } = generateKeyPairSync('x25519');
    return { publicKey: publicKeyToRaw(publicKey), privateKey: privateKeyToRaw(privateKey) };
  },

  x25519Shared(privateKey: Uint8Array, peerPublicKey: Uint8Array): Uint8Array {
    return new Uint8Array(
      diffieHellman({ privateKey: rawToPrivateKey(privateKey), publicKey: rawToPublicKey(peerPublicKey) }),
    );
  },

  hkdf(ikm: Uint8Array, salt: Uint8Array, info: Uint8Array, length: number): Uint8Array {
    return new Uint8Array(hkdfSync('sha256', ikm, salt, info, length));
  },

  hmac(key: Uint8Array, data: Uint8Array): Uint8Array {
    return new Uint8Array(createHmac('sha256', Buffer.from(key)).update(Buffer.from(data)).digest());
  },

  aesGcmSeal(key: Uint8Array, nonce: Uint8Array, plaintext: Uint8Array, aad?: Uint8Array): Uint8Array {
    const cipher = createCipheriv('aes-256-gcm', Buffer.from(key), Buffer.from(nonce));
    if (aad) cipher.setAAD(Buffer.from(aad));
    const ct = Buffer.concat([cipher.update(Buffer.from(plaintext)), cipher.final()]);
    return new Uint8Array(Buffer.concat([ct, cipher.getAuthTag()]));
  },

  aesGcmOpen(key: Uint8Array, nonce: Uint8Array, ciphertext: Uint8Array, aad?: Uint8Array): Uint8Array {
    const tag = ciphertext.slice(ciphertext.length - 16);
    const body = ciphertext.slice(0, ciphertext.length - 16);
    const decipher = createDecipheriv('aes-256-gcm', Buffer.from(key), Buffer.from(nonce));
    decipher.setAuthTag(Buffer.from(tag));
    if (aad) decipher.setAAD(Buffer.from(aad));
    return new Uint8Array(Buffer.concat([decipher.update(Buffer.from(body)), decipher.final()]));
  },
};
