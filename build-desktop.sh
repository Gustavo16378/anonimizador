#!/usr/bin/env bash
# build-desktop.sh — Compila o JAR do backend e empacota o app Electron
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo ""
echo "╔══════════════════════════════════════════════╗"
echo "║       Anonimizador PDF — Build Desktop       ║"
echo "╚══════════════════════════════════════════════╝"
echo ""

# ── 1. Compilar o backend Java ─────────────────────────────────
echo "▶ [1/3] Compilando backend Java (pode demorar alguns minutos)..."
if ! ./mvnw package -DskipTests -Dquarkus.package.jar.type=uber-jar; then
  echo ""
  echo "❌  Falha ao compilar o backend Java."
  echo "    Verifique se o Java 21+ está instalado e tente novamente."
  exit 1
fi
echo "✔  Backend compilado."
echo ""

# ── 2. Instalar dependências do Electron ──────────────────────
echo "▶ [2/3] Instalando dependências do Electron..."
cd desktop
npm install
echo "✔  Dependências instaladas."
echo ""

# ── 3. Empacotar o app ────────────────────────────────────────
echo "▶ [3/3] Empacotando aplicativo desktop..."
npm run dist
echo ""
echo "════════════════════════════════════════════════"
echo "✅  App gerado em:  desktop/dist/"
echo "════════════════════════════════════════════════"
echo ""
