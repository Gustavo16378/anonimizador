const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('api', {
  selectPdf: () => ipcRenderer.invoke('select-pdf'),
  anonymizePdf: (filePath, safeMode) => ipcRenderer.invoke('anonymize-pdf', filePath, safeMode),
  savePdf: (base64Data, originalName) => ipcRenderer.invoke('save-pdf', base64Data, originalName),
  checkBackend: () => ipcRenderer.invoke('check-backend'),
  onBackendReady: (callback) => ipcRenderer.once('backend-ready', callback),
});
