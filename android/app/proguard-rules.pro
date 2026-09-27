# ---------------------------------------------------------------------------
# XHS NoteGen R8/ProGuard rules
#
# release builds enable minification; without these rules, Gson reflection,
# Room and OkHttp internals break at RUNTIME — debug builds never exercise
# this path, so every rule here protects a release-only failure mode.
# ---------------------------------------------------------------------------

# -- Gson: app models are (de)serialized reflectively; field names are the
#    wire contract with both Gemini and the local Room JSON columns.
-keep class com.xiaohan.xhsnotegen.domain.** { *; }
-keep class com.xiaohan.xhsnotegen.data.json.** { *; }
-keep class com.xiaohan.xhsnotegen.ui.generate.GeminiClient { *; }
-keep class com.xiaohan.xhsnotegen.ui.generate.GeminiClient$* { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# -- Gson: enums (values()/valueOf() during (de)serialization)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# -- Room: guard the generated _Impl and entities
#    (room-runtime ships its own consumer rules; these are defense in depth)
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# -- OkHttp / Okio: optional platform integrations referenced reflectively
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
