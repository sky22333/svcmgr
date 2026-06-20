#!/usr/bin/env bash
set -euo pipefail

REPO="sky22333/svcmgr"
TAG="v0.0.1"
JNI_DIR="app/src/main/jniLibs/arm64-v8a"
LIBS_DIR="app/libs"

mkdir -p "$JNI_DIR" "$LIBS_DIR"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

echo "下载 libbox.aar..."
LIBBOX_URL="$(curl -fsSL "https://api.github.com/repos/sky22333/proxy/releases/latest" \
  | grep 'libbox.aar' \
  | head -1 \
  | cut -d '"' -f 4)"
curl -fL "$LIBBOX_URL" -o "$LIBS_DIR/libbox.aar"
echo "libbox.aar 已放入：$LIBS_DIR"

echo "下载 svcmgr release 资源..."
API_URL="https://api.github.com/repos/${REPO}/releases/tags/${TAG}"

curl -fsSL "$API_URL" \
  | grep '"browser_download_url":' \
  | cut -d '"' -f 4 \
  | while read -r url; do
      filename="$(basename "$url")"

      echo "下载: $filename"
      curl -fL "$url" -o "$TMP_DIR/$filename"

      case "$filename" in
        *.so)
          cp "$TMP_DIR/$filename" "$JNI_DIR/"
          ;;
        *.zip)
          unzip -q "$TMP_DIR/$filename" -d "$TMP_DIR/unzip_$filename"
          find "$TMP_DIR/unzip_$filename" -type f -name "*.so" -exec cp {} "$JNI_DIR/" \;
          ;;
        *.tar.gz|*.tgz)
          mkdir -p "$TMP_DIR/tar_$filename"
          tar -xzf "$TMP_DIR/$filename" -C "$TMP_DIR/tar_$filename"
          find "$TMP_DIR/tar_$filename" -type f -name "*.so" -exec cp {} "$JNI_DIR/" \;
          ;;
      esac
    done

echo "删除项目中的所有 .md 文件..."
find . -type f -name "*.md" -delete

echo "完成"
