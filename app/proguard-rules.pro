# Proguard rules for ScreenSaver
-keepattributes *Annotation*
-keepattributes InnerClasses,EnclosingMethod,Signature

# Keep data models so property reflection and state flow work reliably
-keep class com.antigravity.screensaver.model.** { *; }

# Keep HiddenApiBypass runtime helper
-keep class org.lsposed.hiddenapibypass.** { *; }

# Keep DreamService fields accessed via reflection in WirelessChargingDreamService
-keepclassmembers class android.service.dreams.DreamService {
    private boolean mPreviewMode;
    private boolean mShouldShowComplications;
    private ** mOverlayConnection;
    private ** mOverlayCallback;
    *;
}

# Keep Coroutines / Flow
-keepnames class kotlinx.coroutines.** { *; }
