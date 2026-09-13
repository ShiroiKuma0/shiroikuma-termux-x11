# shiroikuma-termux-x11 fork (Phase 4): keep rules for the fork's own code, applied to the release
# build type from shiroikuma.gradle next to upstream's proguard-rules.pro (which stays untouched).

# androidx.preference inflates these by their fully-qualified element name in
# res/xml/preferences_shiroikuma_ui.xml. aapt2 emits keep rules for such names in res/xml, but a
# Preference subclass R8 drops is a crash at page open, on the one screen 白い熊 backs up from.
-keep class com.termux.x11.shiroikuma.** extends androidx.preference.Preference { <init>(...); }
