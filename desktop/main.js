const { app, BrowserWindow, ipcMain, dialog, shell } = require('electron');
const path = require('path');
const { spawn } = require('child_process');
const http = require('http');
const fs = require('fs');
const os = require('os');

let mainWindow;
let backendProcess;
const BACKEND_PORT = 8080;
const BACKEND_URL = `http://localhost:${BACKEND_PORT}`;

// ─── Caminho do JAR (empacotado ou em desenvolvimento) ───────────────────────
function getJarPath() {
  if (app.isPackaged) {
    return path.join(process.resourcesPath, 'backend');
  }
  return path.join(__dirname, '..', 'target');
}

function findJar(dir) {
  if (!fs.existsSync(dir)) return null;
  const files = fs.readdirSync(dir);
  const jar = files.find(f => f.endsWith('-runner.jar'));
  return jar ? path.join(dir, jar) : null;
}

// ─── Inicia o backend Quarkus ────────────────────────────────────────────────
function startBackend() {
  const jarDir = getJarPath();
  const jar = findJar(jarDir);

  if (!jar) {
    console.warn('JAR do backend não encontrado em:', jarDir);
    return;
  }

  console.log('Iniciando backend:', jar);
  backendProcess = spawn('java', ['-jar', jar], {
    env: { ...process.env, QUARKUS_HTTP_PORT: String(BACKEND_PORT) },
    stdio: ['ignore', 'pipe', 'pipe'],
  });

  backendProcess.stdout.on('data', d => console.log('[backend]', d.toString().trim()));
  backendProcess.stderr.on('data', d => console.error('[backend]', d.toString().trim()));
  backendProcess.on('exit', code => console.log('[backend] encerrado, código:', code));
}

// ─── Aguarda o backend responder ─────────────────────────────────────────────
function waitForBackend(retries = 30, delay = 1000) {
  return new Promise((resolve, reject) => {
    const attempt = () => {
      http.get(`${BACKEND_URL}/q/health/ready`, res => {
        if (res.statusCode === 200) {
          resolve();
        } else {
          retry();
        }
      }).on('error', () => retry());
    };
    const retry = () => {
      if (--retries <= 0) {
        resolve(); // abre a janela mesmo que o health check falhe
      } else {
        setTimeout(attempt, delay);
      }
    };
    attempt();
  });
}

// ─── Cria a janela principal ──────────────────────────────────────────────────
function createWindow() {
  mainWindow = new BrowserWindow({
    width: 900,
    height: 680,
    minWidth: 700,
    minHeight: 560,
    title: 'Anonimizador PDF',
    backgroundColor: '#0f172a',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
    show: false,
    icon: path.join(__dirname, 'assets', 'icon.png'),
  });

  mainWindow.loadFile(path.join(__dirname, 'index.html'));

  mainWindow.once('ready-to-show', () => mainWindow.show());

  // Abre links externos no navegador padrão
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: 'deny' };
  });
}

// ─── IPC: abrir diálogo de arquivo ───────────────────────────────────────────
ipcMain.handle('select-pdf', async () => {
  const { canceled, filePaths } = await dialog.showOpenDialog(mainWindow, {
    title: 'Selecionar PDF',
    filters: [{ name: 'PDF', extensions: ['pdf'] }],
    properties: ['openFile'],
  });
  if (canceled || !filePaths[0]) return null;
  const filePath = filePaths[0];
  const { size } = fs.statSync(filePath);
  return { path: filePath, size };
});

// ─── IPC: anonimizar PDF ──────────────────────────────────────────────────────
ipcMain.handle('anonymize-pdf', async (_event, filePath, safeMode) => {
  const fileBuffer = fs.readFileSync(filePath);

  return new Promise((resolve) => {
    const options = {
      hostname: 'localhost',
      port: BACKEND_PORT,
      path: `/anonimizador/redact?safe=${safeMode}`,
      method: 'POST',
      headers: {
        'Content-Type': 'application/octet-stream',
        'Content-Length': fileBuffer.length,
      },
    };

    const req = http.request(options, res => {
      const chunks = [];
      res.on('data', chunk => chunks.push(chunk));
      res.on('end', () => {
        if (res.statusCode === 200) {
          resolve({ success: true, data: Buffer.concat(chunks).toString('base64') });
        } else {
          resolve({ success: false, error: `Erro do servidor: ${res.statusCode}` });
        }
      });
    });

    req.on('error', err => resolve({ success: false, error: err.message }));
    req.write(fileBuffer);
    req.end();
  });
});

// ─── IPC: salvar PDF anonimizado ─────────────────────────────────────────────
ipcMain.handle('save-pdf', async (_event, base64Data, originalName) => {
  const defaultName = originalName
    ? originalName.replace(/\.pdf$/i, '_anonimizado.pdf')
    : 'documento_anonimizado.pdf';

  const { canceled, filePath } = await dialog.showSaveDialog(mainWindow, {
    title: 'Salvar PDF Anonimizado',
    defaultPath: path.join(os.homedir(), defaultName),
    filters: [{ name: 'PDF', extensions: ['pdf'] }],
  });

  if (canceled || !filePath) return { success: false };

  fs.writeFileSync(filePath, Buffer.from(base64Data, 'base64'));
  return { success: true, filePath };
});

// ─── IPC: checar status do backend ───────────────────────────────────────────
ipcMain.handle('check-backend', () => {
  return new Promise(resolve => {
    http.get(`${BACKEND_URL}/q/health/ready`, res => {
      resolve(res.statusCode === 200);
    }).on('error', () => resolve(false));
  });
});

// ─── Ciclo de vida do app ─────────────────────────────────────────────────────
app.whenReady().then(async () => {
  startBackend();
  createWindow();

  // Aguarda backend em background e notifica a UI
  waitForBackend().then(() => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('backend-ready');
    }
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) createWindow();
});

app.on('before-quit', () => {
  if (backendProcess) {
    backendProcess.kill();
  }
});
