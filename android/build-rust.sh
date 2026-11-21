#!/usr/bin/env bash
# Compila a ponte Rust (rust-bridge/ → core/) pra Android e gera os bindings Kotlin.
# O Gradle chama este script antes de cada build; dá pra rodar sozinho também.
set -euo pipefail
cd "$(dirname "$0")"
export PATH="$HOME/.cargo/bin:$PATH"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$(ls -d "$SDK"/ndk/* | sort -V | tail -1)}"

OUT="$PWD/app/build/rust"
cd rust-bridge
# Sempre em release: o OCR em modo debug fica lento demais pra testar.
cargo ndk -t arm64-v8a --platform 29 -o "$OUT/jniLibs" build --release --lib
cargo run --release -q --bin uniffi-bindgen -- generate \
  --library "$OUT/jniLibs/arm64-v8a/libwebtoon_android.so" \
  --language kotlin --no-format --out-dir "$OUT/kotlin"
