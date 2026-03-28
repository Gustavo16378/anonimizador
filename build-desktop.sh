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
echo "▶ [1/4] Compilando backend Java (pode demorar alguns minutos)..."
if ! ./mvnw package -DskipTests -Dquarkus.package.jar.type=uber-jar; then
  echo ""
  echo "❌  Falha ao compilar o backend Java."
  echo "    Verifique se o Java 21+ está instalado e tente novamente."
  exit 1
fi
echo "✔  Backend compilado."
echo ""

# ── 2. Baixar o JRE para empacotar ──────────────────────────────
JRE_DIR="$SCRIPT_DIR/desktop/jre"
if [ -d "$JRE_DIR" ]; then
  echo "▶ [2/4] JRE já encontrado em desktop/jre — pulando download."
else
  echo "▶ [2/4] Baixando JRE 21 para empacotar com o app..."
  OS="$(uname -s)"
  ARCH="$(uname -m)"

  case "$OS" in
    Linux*)   ADOPTIUM_OS="linux"  ;;
    Darwin*)  ADOPTIUM_OS="mac"    ;;
    MINGW*|MSYS*|CYGWIN*) ADOPTIUM_OS="windows" ;;
    *)
      echo "❌  Sistema operacional não reconhecido: $OS"
      echo "    Baixe manualmente o JRE 21 de https://adoptium.net e extraia em desktop/jre/"
      exit 1
      ;;
  esac

  case "$ARCH" in
    x86_64|amd64) ADOPTIUM_ARCH="x64"   ;;
    aarch64|arm64) ADOPTIUM_ARCH="aarch64" ;;
    *)
      echo "❌  Arquitetura não reconhecida: $ARCH"
      echo "    Baixe manualmente o JRE 21 de https://adoptium.net e extraia em desktop/jre/"
      exit 1
      ;;
  esac

  JRE_URL="https://api.adoptium.net/v3/binary/latest/21/ga/${ADOPTIUM_OS}/${ADOPTIUM_ARCH}/jre/hotspot/normal/eclipse"
  TMP_ARCHIVE="/tmp/jre-download"

  echo "    URL: $JRE_URL"

  if command -v curl &>/dev/null; then
    curl -fL -o "$TMP_ARCHIVE" "$JRE_URL"
  elif command -v wget &>/dev/null; then
    wget -q -O "$TMP_ARCHIVE" "$JRE_URL"
  else
    echo "❌  curl ou wget não encontrado. Instale um deles e tente novamente."
    exit 1
  fi

  mkdir -p /tmp/jre-extracted
  if [[ "$ADOPTIUM_OS" == "windows" ]]; then
    unzip -q "$TMP_ARCHIVE" -d /tmp/jre-extracted
  else
    tar -xzf "$TMP_ARCHIVE" -C /tmp/jre-extracted
  fi

  shopt -s nullglob
  extracted_dirs=(/tmp/jre-extracted/*)
  if [ ${#extracted_dirs[@]} -ne 1 ]; then
    echo "❌  Extração do JRE falhou ou produziu resultado inesperado."
    exit 1
  fi
  mv "${extracted_dirs[0]}" "$JRE_DIR"
  rm -rf /tmp/jre-extracted "$TMP_ARCHIVE"
  echo "✔  JRE baixado e extraído."
fi
echo ""

# ── 3. Instalar dependências do Electron ──────────────────────
echo "▶ [3/4] Instalando dependências do Electron..."
cd desktop
npm install
echo "✔  Dependências instaladas."
echo ""

# ── 4. Empacotar o app ────────────────────────────────────────
echo "▶ [4/4] Empacotando aplicativo desktop..."
npm run dist
echo ""
echo "════════════════════════════════════════════════"
echo "✅  App gerado em:  desktop/dist/"
echo "════════════════════════════════════════════════"
echo ""
