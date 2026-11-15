package com.example.bridge

/**
 * Generates the exact navigator.usb WebUSB Polyfill / Mock JavaScript code
 * compatible with ya-webadb, Tango, WebADB, and online flasher tools,
 * with dynamic WebSocket port injection.
 */
object WebUsbJsGenerator {

    /**
     * Exact multi-line JavaScript polyfill injected into WebView onPageStarted / onPageFinished
     * and available for raw console copy.
     */
    fun buildPolyfillScript(wsPort: Int = 8765): String = """
(function(){
  if (window.__webUsbBridgeInjected) return;
  window.__webUsbBridgeInjected = true;
  let ws = null;
  let q = [];
  let w = [];
  const epIn = {endpointNumber: 1, direction: "in", type: "bulk", packetSize: 512};
  const epOut = {endpointNumber: 2, direction: "out", type: "bulk", packetSize: 512};
  const alt = {alternateSetting: 0, interfaceClass: 255, interfaceSubclass: 66, interfaceProtocol: 1, interfaceName: "ADB Interface", endpoints: [epIn, epOut]};
  const iface = {interfaceNumber: 0, alternate: alt, alternates: [alt], claimed: true};
  const conf = {configurationValue: 1, configurationName: "ADB Config", interfaces: [iface]};
  const fakeDev = {
    usbVersionMajor: 2, usbVersionMinor: 0, usbVersionSubminor: 0,
    deviceClass: 0, deviceSubclass: 0, deviceProtocol: 0,
    vendorId: 0x18d1, productId: 0x4ee7,
    deviceVersionMajor: 1, deviceVersionMinor: 0, deviceVersionSubminor: 0,
    manufacturerName: "Android", productName: "Loopback ADB", serialNumber: "LOCALHOST",
    configuration: conf, configurations: [conf], opened: false,
    open: async () => {
      fakeDev.opened = true;
      if (ws && ws.readyState === 1) return;
      ws = new WebSocket("ws://127.0.0.1:$wsPort");
      ws.binaryType = "arraybuffer";
      ws.onmessage = (e) => {
        if (w.length > 0) { w.shift()(e.data); } else { q.push(e.data); }
      };
      return new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej; });
    },
    selectConfiguration: async () => {},
    claimInterface: async () => { iface.claimed = true; },
    selectAlternateInterface: async () => {},
    releaseInterface: async () => {},
    clearHalt: async () => {},
    reset: async () => {},
    close: async () => { fakeDev.opened = false; if (ws) ws.close(); },
    controlTransferIn: async () => ({data: new DataView(new ArrayBuffer(0)), status: "ok"}),
    controlTransferOut: async () => ({bytesWritten: 0, status: "ok"}),
    transferOut: async (ep, data) => {
      let buf = data instanceof ArrayBuffer ? data : new Uint8Array(data.buffer, data.byteOffset, data.byteLength);
      ws.send(buf);
      return {bytesWritten: buf.byteLength, status: "ok"};
    },
    transferIn: async (ep, len) => {
      let buf = q.length > 0 ? q.shift() : await new Promise(r => w.push(r));
      return {data: new DataView(buf), status: "ok"};
    }
  };
  Object.defineProperty(navigator, "usb", {
    value: {
      getDevices: async () => [fakeDev],
      requestDevice: async () => fakeDev,
      addEventListener: () => {},
      removeEventListener: () => {}
    },
    configurable: true
  });
  console.log("[WebUSB Bridge] Basariyla enjekte edildi!");
})();
    """.trimIndent()

    /**
     * Single-line Bookmarklet URI for Brave / Chrome bookmark bar execution.
     */
    fun buildBookmarklet(wsPort: Int = 8765): String {
        val compact = buildPolyfillScript(wsPort)
            .lines()
            .joinToString(" ") { it.trim() }
        return "javascript:$compact"
    }

    /**
     * Built-in local WebUSB Diagnostic & ADB Handshake Test Page (`local://inspector`)
     * so users can immediately verify `navigator.usb`, 24-byte ADB header splitting, and
     * payload framing directly inside the embedded WebView without needing external internet.
     */
    fun buildLocalTestConsoleHtml(wsPort: Int, adbPort: Int): String = """
<!DOCTYPE html>
<html lang="tr">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<title>WebUSB Local Bridge — Protokol Test Konsolu</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    background: #090D16;
    color: #F8FAFC;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    padding: 14px;
    font-size: 12px;
    line-height: 1.5;
  }
  .header {
    border: 1px solid #334155;
    background: #111827;
    padding: 12px;
    border-radius: 6px;
    margin-bottom: 12px;
  }
  .title {
    font-size: 14px;
    font-weight: 700;
    color: #10B981;
    margin-bottom: 4px;
  }
  .meta { color: #94A3B8; font-size: 11px; }
  .grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;
    margin-bottom: 12px;
  }
  button {
    background: #1E293B;
    color: #F8FAFC;
    border: 1px solid #334155;
    padding: 10px 12px;
    border-radius: 6px;
    font-family: inherit;
    font-size: 12px;
    font-weight: 600;
    cursor: pointer;
    text-align: left;
  }
  button.primary {
    background: #064E3B;
    border-color: #10B981;
    color: #A7F3D0;
  }
  button:active { opacity: 0.8; }
  .status-box {
    border: 1px solid #334155;
    background: #111827;
    padding: 10px;
    border-radius: 6px;
    margin-bottom: 12px;
  }
  .log {
    border: 1px solid #334155;
    background: #05080F;
    padding: 10px;
    border-radius: 6px;
    height: 290px;
    overflow-y: auto;
    white-space: pre-wrap;
    word-break: break-all;
  }
  .ok { color: #10B981; }
  .warn { color: #F59E0B; }
  .err { color: #EF4444; }
  .info { color: #38BDF8; }
</style>
</head>
<body>
  <div class="header">
    <div class="title">WEBUSB LOOPBACK DIAGNOSTIC BENCH</div>
    <div class="meta">WS KÖPRÜ: ws://127.0.0.1:$wsPort &nbsp;|&nbsp; HEDEF ADB: 127.0.0.1:$adbPort</div>
  </div>

  <div class="status-box" id="usbState">
    navigator.usb durumu kontrol ediliyor...
  </div>

  <div class="grid">
    <button class="primary" onclick="testAdbHandshake()">1. ADB CNXN Handshake Testi</button>
    <button onclick="inspectUsbObject()">2. navigator.usb Şemasını Denetle</button>
    <button onclick="closeUsbDevice()">3. Bağlantıyı Kapat (close)</button>
    <button onclick="clearConsole()">4. Günlüğü Temizle</button>
  </div>

  <div class="log" id="logBox"></div>

<script>
  const logBox = document.getElementById('logBox');
  const usbState = document.getElementById('usbState');

  function log(msg, cls = '') {
    const line = document.createElement('div');
    if (cls) line.className = cls;
    const now = new Date().toLocaleTimeString('tr-TR', { hour12: false });
    line.textContent = '[' + now + '] ' + msg;
    logBox.appendChild(line);
    logBox.scrollTop = logBox.scrollHeight;
  }

  function clearConsole() {
    logBox.innerHTML = '';
  }

  function updateStatus() {
    if (navigator.usb && window.__webUsbBridgeInjected) {
      usbState.innerHTML = '<span class="ok">● AKTİF:</span> navigator.usb Polyfill enjekte edildi (VID: 0x18d1, PID: 0x4ee7)';
    } else {
      usbState.innerHTML = '<span class="err">● PASİF:</span> navigator.usb henüz enjekte edilmedi';
    }
  }

  async function inspectUsbObject() {
    updateStatus();
    try {
      const dev = await navigator.usb.requestDevice({ filters: [] });
      const alt = dev.configurations[0].interfaces[0].alternates[0];
      log('Cihaz seçildi: ' + dev.manufacturerName + ' / ' + dev.productName + ' (' + dev.serialNumber + ')', 'ok');
      log('Interface #0 Class: ' + alt.interfaceClass + ', Subclass: ' + alt.interfaceSubclass + ', Protocol: ' + alt.interfaceProtocol, 'info');
      log('Endpoint IN: #' + alt.endpoints[0].endpointNumber + ' (' + alt.endpoints[0].direction + ', ' + alt.endpoints[0].packetSize + 'B)', 'info');
      log('Endpoint OUT: #' + alt.endpoints[1].endpointNumber + ' (' + alt.endpoints[1].direction + ', ' + alt.endpoints[1].packetSize + 'B)', 'info');
    } catch (e) {
      log('Hata: ' + e, 'err');
    }
  }

  let activeDev = null;

  async function testAdbHandshake() {
    updateStatus();
    try {
      log('navigator.usb.requestDevice() çağrılıyor...', 'info');
      activeDev = await navigator.usb.requestDevice({ filters: [{ vendorId: 0x18d1 }] });
      log('device.open() -> ws://127.0.0.1:$wsPort bağlanılıyor...', 'warn');
      await activeDev.open();
      await activeDev.selectConfiguration(1);
      await activeDev.claimInterface(0);
      log('WebSocket köprüsü açıldı! 24-bayt ADB CNXN paketi hazırlanıyor...', 'ok');

      const banner = new TextEncoder().encode('host::features=shell_v2,cmd,stat_v2\0');
      const header = new ArrayBuffer(24);
      const view = new DataView(header);
      view.setUint32(0, 0x4e584e43, true); // 'CNXN' little-endian
      view.setUint32(4, 0x01000000, true); // version
      view.setUint32(8, 1048576, true);    // maxdata
      view.setUint32(12, banner.byteLength, true); // data_len
      let sum = 0;
      for (let i = 0; i < banner.length; i++) sum = (sum + banner[i]) >>> 0;
      view.setUint32(16, sum, true);       // data_crc32
      view.setUint32(20, (0x4e584e43 ^ 0xffffffff) >>> 0, true); // magic

      log('transferOut(ep=2, 24B Header) gönderiliyor...', 'info');
      await activeDev.transferOut(2, header);
      log('transferOut(ep=2, ' + banner.byteLength + 'B Payload) gönderiliyor...', 'info');
      await activeDev.transferOut(2, banner.buffer);

      log('transferIn #1 (24-bayt ADB Başlığı bekleniyor)...', 'warn');
      const resHeader = await activeDev.transferIn(1, 24);
      const hView = resHeader.data;
      const cmdBytes = new Uint8Array(hView.buffer, hView.byteOffset, 4);
      const cmdStr = new TextDecoder().decode(cmdBytes);
      const dataLen = hView.getUint32(12, true);
      log('transferIn #1 ALINDI -> Boyut: ' + hView.byteLength + ' bayt | Komut: ' + cmdStr + ' | data_len: ' + dataLen, 'ok');

      if (dataLen > 0) {
        log('transferIn #2 (' + dataLen + ' bayt Payload bekleniyor)...', 'warn');
        const resPayload = await activeDev.transferIn(1, dataLen);
        const pBytes = new Uint8Array(resPayload.data.buffer, resPayload.data.byteOffset, resPayload.data.byteLength);
        const textPreview = new TextDecoder().decode(pBytes).replace(/[^\x20-\x7E]/g, '.');
        log('transferIn #2 ALINDI -> Boyut: ' + resPayload.data.byteLength + ' bayt | İçerik: ' + textPreview, 'ok');
      }
    } catch (e) {
      log('Handshake Hatası (Köprü servisi ve ADB 5555 portunun açık olduğundan emin olun): ' + (e && e.message ? e.message : JSON.stringify(e)), 'err');
    }
  }

  async function closeUsbDevice() {
    if (activeDev) {
      await activeDev.close();
      log('Cihaz bağlantısı kapatıldı (device.close).', 'warn');
    } else {
      log('Açık bağlantı yok.', 'info');
    }
  }

  setTimeout(updateStatus, 150);
  log('Test konsolu hazır. Harici WebADB siteleri için üstteki URL çubuğunu kullanabilirsiniz.', 'info');
</script>
</body>
</html>
    """.trimIndent()
}
