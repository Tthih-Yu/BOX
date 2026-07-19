#!/usr/bin/env sh
# 生成内网自签 TLS 证书，供 nginx-tls.conf 使用。
# 用法：
#   CERT_CN=material-pull.local CERT_IP=192.168.1.50 ./gen-self-signed-cert.sh
# 生成结果默认写到 deploy/certs/server.crt 与 server.key。
set -eu

OUT_DIR="${OUT_DIR:-$(cd "$(dirname "$0")/.." && pwd)/certs}"
CERT_CN="${CERT_CN:-material-pull.local}"
CERT_IP="${CERT_IP:-}"
DAYS="${DAYS:-3650}"

mkdir -p "$OUT_DIR"

SAN="DNS:${CERT_CN},DNS:localhost"
if [ -n "$CERT_IP" ]; then
  SAN="${SAN},IP:${CERT_IP}"
fi

echo "[cert] 生成自签证书 CN=${CERT_CN} SAN=${SAN} 有效期=${DAYS}天"
openssl req -x509 -nodes -newkey rsa:2048 \
  -keyout "$OUT_DIR/server.key" \
  -out "$OUT_DIR/server.crt" \
  -days "$DAYS" \
  -subj "/C=CN/O=MaterialPull/CN=${CERT_CN}" \
  -addext "subjectAltName=${SAN}"

chmod 600 "$OUT_DIR/server.key"
echo "[cert] 完成：$OUT_DIR/server.crt , $OUT_DIR/server.key"
echo "[cert] 自签证书浏览器会提示不受信任，可导入内部 CA 或在客户端信任该证书。"
