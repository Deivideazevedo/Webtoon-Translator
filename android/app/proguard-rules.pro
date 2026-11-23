# Regras do R8 (versão de release). O resto do app não usa reflexão.

# JNA: a ponte Kotlin↔Rust (UniFFI) acha classes, campos e callbacks por reflexão.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-dontwarn java.awt.**

# Bindings gerados pelo UniFFI (uniffi.webtoon_android): estruturas e callbacks vistos pela JNA.
-keep class uniffi.** { *; }
