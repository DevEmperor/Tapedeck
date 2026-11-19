#!/usr/bin/env bash
# Fetches QuickJS-NG's static Linux builds and puts them into app/src/main/jniLibs as libqjsng.so.
#
# yt-dlp needs a JavaScript runtime for YouTube. youtubedl-android ships QuickJS 2025-04-26 (libqjs.so);
# QuickJS-NG solves YouTube's challenges noticeably faster (measured in the commit that added it). The
# release binaries are static-pie executables, so they run on Android as they are; Engine checks once per
# app version that they do and otherwise falls back to libqjs.so.
#
#   tools/fetch-quickjs-ng.sh            # the version below
#   NDK=/path/to/ndk tools/fetch-quickjs-ng.sh
set -euo pipefail

VERSION=v0.18.0
# SHA-256 as listed by GitHub for the release assets.
declare -A SHA256=(
  [aarch64]=2c005c7171424a598d31c53c8dc4a0ae2e4958eb6fbb37908c885a307c78187c
  [armv7]=4bbc426083b4a31f35cbb9471ce6138a8d8db8efdfce15b596597ad60ddd79ac
  [x86]=6e4e46849f6450a0afc572b8d10a0178d70040314f39669ca98ca8e95f114648
  [x86_64]=85ded23b9fabe8c1644fc40a8d2bbdd71157affcbd807359c618cc717ee24d7d
)
declare -A ABI=([aarch64]=arm64-v8a [armv7]=armeabi-v7a [x86]=x86 [x86_64]=x86_64)

ROOT=$(cd "$(dirname "$0")/.." && pwd)
NDK=${NDK:-$(ls -d "$HOME"/.android/SDK/ndk/* 2>/dev/null | sort -V | tail -1)}
STRIP=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

for arch in aarch64 armv7 x86 x86_64; do
  file="qjs-linux-$arch"
  curl -fsSL -o "$WORK/$file" "https://github.com/quickjs-ng/quickjs/releases/download/$VERSION/$file"
  echo "${SHA256[$arch]}  $WORK/$file" | sha256sum -c --quiet
  mkdir -p "$ROOT/app/src/main/jniLibs/${ABI[$arch]}"
  "$STRIP" --strip-all -o "$ROOT/app/src/main/jniLibs/${ABI[$arch]}/libqjsng.so" "$WORK/$file"
  echo "${ABI[$arch]}: QuickJS-NG $VERSION"
done
