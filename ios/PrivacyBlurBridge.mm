#import <React/RCTBridgeModule.h>
#import <React/RCTLog.h>

// Swift implementation is in PrivacyBlur.swift.
// We expose its methods to React Native via RCT_EXTERN_*

@interface RCT_EXTERN_MODULE(PrivacyBlur, NSObject)
RCT_EXTERN_METHOD(configure:(NSDictionary *)conf)
RCT_EXTERN_METHOD(enable)
RCT_EXTERN_METHOD(disable)
RCT_EXTERN_METHOD(isEnabled:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(showNow)
RCT_EXTERN_METHOD(hideNow)
@end
