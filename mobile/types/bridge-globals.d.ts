/**
 * Ambient declarations for standard runtime globals the RN type config omits but
 * Hermes provides (used by @mias/bridge-protocol's crypto/framing). Runtime is
 * unaffected; this only satisfies the typechecker.
 */
declare function btoa(data: string): string;
declare function atob(data: string): string;

declare class TextEncoder {
  encode(input?: string): Uint8Array;
}
declare class TextDecoder {
  constructor(label?: string);
  decode(input?: Uint8Array | ArrayBuffer): string;
}
