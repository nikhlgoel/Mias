//
//  MiasSecurity.m — RCT bridge for the Swift MiasSecurity module.
//
//  ⚠️ UNVERIFIED (no Xcode here). Exposes the Swift class to the RN bridge with
//  the exact method signatures the TS wrapper calls. Under the New Architecture
//  these classic RCT_EXTERN modules run via the interop layer; the fully-typed
//  path is a TurboModule codegen spec, tracked as a follow-up.
//
#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(MiasSecurity, NSObject)

RCT_EXTERN_METHOD(secureSet:(NSString *)key value:(NSString *)value
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(secureGet:(NSString *)key
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(secureRemove:(NSString *)key
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(hasSecret:(NSString *)key
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(authenticate:(NSString *)title subtitle:(NSString *)subtitle
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

@end
