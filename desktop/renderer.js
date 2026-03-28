/* ── State ─────────────────────────────────────────────────── */
let selectedFile = null;     // { path, name, size }
let resultB64    = null;     // base64 do PDF anonimizado
let backendReady = false;

/* ── DOM refs ──────────────────────────────────────────────── */
const dropzone     = document.getElementById('dropzone');
const fileInfo     = document.getElementById('file-info');
const fileNameEl   = document.getElementById('file-name');
const fileSizeEl   = document.getElementById('file-size');
const btnRemove    = document.getElementById('btn-remove');
const btnAnonymize = document.getElementById('btn-anonymize');
const safeMode     = document.getElementById('safe-mode');
const progressWrap = document.getElementById('progress-wrap');
const progressFill = document.getElementById('progress-fill');
const progressLbl  = document.getElementById('progress-label-text');
const resultArea   = document.getElementById('result-area');
const resultSub    = document.getElementById('result-sub');
const btnNew       = document.getElementById('btn-new');
const btnSave      = document.getElementById('btn-save');
const statusDot    = document.getElementById('status-dot');
const statusText   = document.getElementById('status-text');

/* ── Status do backend ─────────────────────────────────────── */
function setStatus(state) {
  statusDot.className = 'status-dot';
  if (state === 'ready') {
    statusDot.classList.add('ready');
    statusText.textContent = 'Servidor pronto';
  } else if (state === 'offline') {
    statusDot.classList.add('offline');
    statusText.textContent = 'Servidor offline';
  } else {
    statusText.textContent = 'Iniciando servidor…';
  }
}

// Evento disparado pelo processo principal quando o backend sobe
window.api.onBackendReady(() => {
  backendReady = true;
  setStatus('ready');
  updateButton();
});

// Verificação periódica (caso a janela abra após o backend já estar pronto)
async function pollBackend() {
  const ok = await window.api.checkBackend();
  if (ok) {
    backendReady = true;
    setStatus('ready');
    updateButton();
  } else {
    setTimeout(pollBackend, 2000);
  }
}
pollBackend();

/* ── Helpers ───────────────────────────────────────────────── */
function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function updateButton() {
  btnAnonymize.disabled = !(selectedFile && backendReady && !progressWrap.classList.contains('visible'));
}

function showToast(msg, type = '') {
  const t = document.getElementById('toast');
  t.textContent = msg;
  t.className = `show ${type}`;
  setTimeout(() => { t.className = ''; }, 3200);
}

/* ── Seleção de arquivo ────────────────────────────────────── */
function setFile(file) {
  selectedFile = file;
  fileNameEl.textContent = file.name;
  fileSizeEl.textContent = formatBytes(file.size);
  fileInfo.classList.add('visible');
  dropzone.classList.add('has-file');
  resultArea.classList.remove('visible');
  resultB64 = null;
  updateButton();
}

function clearFile() {
  selectedFile = null;
  fileInfo.classList.remove('visible');
  dropzone.classList.remove('has-file');
  updateButton();
}

// Clique na dropzone → abre diálogo
dropzone.addEventListener('click', async (e) => {
  if (e.target === btnRemove) return;
  const filePath = await window.api.selectPdf();
  if (!filePath) return;
  const name = filePath.path.split(/[\\/]/).pop();
  setFile({ path: filePath.path, name, size: filePath.size });
});

btnRemove.addEventListener('click', (e) => {
  e.stopPropagation();
  clearFile();
});

// Drag & drop visual
dropzone.addEventListener('dragover',  (e) => { e.preventDefault(); dropzone.classList.add('drag-over'); });
dropzone.addEventListener('dragleave', ()  => dropzone.classList.remove('drag-over'));
dropzone.addEventListener('drop', (e) => {
  e.preventDefault();
  dropzone.classList.remove('drag-over');
  const file = e.dataTransfer.files[0];
  if (!file) return;
  if (!file.name.toLowerCase().endsWith('.pdf')) {
    showToast('Por favor, selecione apenas arquivos PDF.', 'error');
    return;
  }
  // No Electron, file.path está disponível
  setFile({ path: file.path, name: file.name, size: file.size });
});

/* ── Anonimizar ────────────────────────────────────────────── */
btnAnonymize.addEventListener('click', async () => {
  if (!selectedFile || !backendReady) return;

  // Exibe progresso
  progressWrap.classList.add('visible');
  progressFill.className = 'progress-bar-fill indeterminate';
  progressLbl.textContent = 'Enviando documento…';
  resultArea.classList.remove('visible');
  btnAnonymize.disabled = true;

  try {
    const safe = safeMode.checked;
    progressLbl.textContent = 'Anonimizando dados sensíveis…';

    const result = await window.api.anonymizePdf(selectedFile.path, safe);

    if (result.success) {
      resultB64 = result.data;
      progressWrap.classList.remove('visible');
      resultArea.classList.add('visible');
      resultSub.textContent = `Arquivo: ${selectedFile.name}`;
      showToast('Documento anonimizado com sucesso!', 'success');
    } else {
      throw new Error(result.error || 'Erro desconhecido');
    }
  } catch (err) {
    progressWrap.classList.remove('visible');
    showToast(`Erro: ${err.message}`, 'error');
  } finally {
    updateButton();
  }
});

/* ── Salvar PDF ────────────────────────────────────────────── */
btnSave.addEventListener('click', async () => {
  if (!resultB64) return;
  const result = await window.api.savePdf(resultB64, selectedFile?.name);
  if (result.success) {
    showToast('Arquivo salvo com sucesso!', 'success');
  }
});

/* ── Novo documento ────────────────────────────────────────── */
btnNew.addEventListener('click', () => {
  clearFile();
  resultArea.classList.remove('visible');
  resultB64 = null;
});
