//
//  MiasSecurity.swift
//  Reference iOS implementation of the `MiasSecurity` native module.
//
//  ⚠️ UNVERIFIED: written on a Windows environment with no Xcode. This is the
//  reference pattern (see docs/IOS_AND_DESKTOP.md) — it must be added to the
//  Xcode target, compiled, and tested on a device/simulator before use.
//
//  It exposes the SAME method names the TS wrapper (mobile/src/native/security.ts)
//  calls, so `NativeModules.MiasSecurity` works unchanged on iOS:
//    secureGet / secureSet / secureRemove / hasSecret / authenticate
//
//  Android's ZkVault (Keystore-backed EncryptedSharedPreferences) maps to the
//  iOS Keychain; BiometricPrompt maps to LocalAuthentication (FaceID/TouchID).
//

import Foundation
import LocalAuthentication
import React

@objc(MiasSecurity)
class MiasSecurity: NSObject {

  private let service = "app.mias.vault"

  @objc static func requiresMainQueueSetup() -> Bool { false }

  // MARK: - Keychain (maps to Android ZkVault)

  @objc(secureSet:value:resolver:rejecter:)
  func secureSet(_ key: String, value: String,
                 resolver resolve: RCTPromiseResolveBlock,
                 rejecter reject: RCTPromiseRejectBlock) {
    let data = Data(value.utf8)
    var query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: service,
      kSecAttrAccount as String: key,
    ]
    SecItemDelete(query as CFDictionary) // upsert
    query[kSecValueData as String] = data
    query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
    let status = SecItemAdd(query as CFDictionary, nil)
    if status == errSecSuccess { resolve(nil) }
    else { reject("vault_set", "Keychain write failed (\(status))", nil) }
  }

  @objc(secureGet:resolver:rejecter:)
  func secureGet(_ key: String,
                 resolver resolve: RCTPromiseResolveBlock,
                 rejecter reject: RCTPromiseRejectBlock) {
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: service,
      kSecAttrAccount as String: key,
      kSecReturnData as String: true,
      kSecMatchLimit as String: kSecMatchLimitOne,
    ]
    var item: CFTypeRef?
    let status = SecItemCopyMatching(query as CFDictionary, &item)
    if status == errSecSuccess, let data = item as? Data {
      resolve(String(decoding: data, as: UTF8.self))
    } else {
      resolve(nil) // absent, not an error (matches Android)
    }
  }

  @objc(secureRemove:resolver:rejecter:)
  func secureRemove(_ key: String,
                    resolver resolve: RCTPromiseResolveBlock,
                    rejecter reject: RCTPromiseRejectBlock) {
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: service,
      kSecAttrAccount as String: key,
    ]
    SecItemDelete(query as CFDictionary)
    resolve(nil)
  }

  @objc(hasSecret:resolver:rejecter:)
  func hasSecret(_ key: String,
                 resolver resolve: RCTPromiseResolveBlock,
                 rejecter reject: RCTPromiseRejectBlock) {
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: service,
      kSecAttrAccount as String: key,
      kSecMatchLimit as String: kSecMatchLimitOne,
    ]
    resolve(SecItemCopyMatching(query as CFDictionary, nil) == errSecSuccess)
  }

  // MARK: - Biometric re-auth (maps to Android BiometricPrompt, strong)

  @objc(authenticate:subtitle:resolver:rejecter:)
  func authenticate(_ title: String, subtitle: String,
                    resolver resolve: @escaping RCTPromiseResolveBlock,
                    rejecter reject: @escaping RCTPromiseRejectBlock) {
    let context = LAContext()
    var error: NSError?
    // .deviceOwnerAuthenticationWithBiometrics == FaceID/TouchID only (Class 3
    // equivalent). Fall back to false when unavailable, mirroring Android.
    guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
      resolve(false)
      return
    }
    let reason = subtitle.isEmpty ? "Only you can access Mias" : subtitle
    context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason) { success, _ in
      resolve(success)
    }
  }
}
