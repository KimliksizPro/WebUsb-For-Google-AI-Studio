package com.example.bridge

/**
 * Generates the WebUSB v2 Polyfill / Bookmarklet JavaScript code
 * compatible with ya-webadb, Tango, WebADB, and online flasher tools,
 * with dynamic WebSocket port injection.
 */
object WebUsbJsGenerator {

    /**
     * Formatted JavaScript polyfill available for raw console copy and inspection.
     */
    fun buildPolyfillScript(wsPort: Int = 8765): String = """
(function(){
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
  alert("WebUSB v2 Aktif!");
})();
    """.trimIndent()

    /**
     * Single-line Bookmarklet URI for Brave / Chrome bookmark bar execution.
     */
    fun buildBookmarklet(wsPort: Int = 8765): String =
        """javascript:(function(){let ws=null;let q=[];let w=[];const epIn={endpointNumber:1,direction:"in",type:"bulk",packetSize:512};const epOut={endpointNumber:2,direction:"out",type:"bulk",packetSize:512};const alt={alternateSetting:0,interfaceClass:255,interfaceSubclass:66,interfaceProtocol:1,interfaceName:"ADB Interface",endpoints:[epIn,epOut]};const iface={interfaceNumber:0,alternate:alt,alternates:[alt],claimed:true};const conf={configurationValue:1,configurationName:"ADB Config",interfaces:[iface]};const fakeDev={usbVersionMajor:2,usbVersionMinor:0,usbVersionSubminor:0,deviceClass:0,deviceSubclass:0,deviceProtocol:0,vendorId:0x18d1,productId:0x4ee7,deviceVersionMajor:1,deviceVersionMinor:0,deviceVersionSubminor:0,manufacturerName:"Android",productName:"Loopback ADB",serialNumber:"LOCALHOST",configuration:conf,configurations:[conf],opened:false,open:async()=>{fakeDev.opened=true;if(ws&&ws.readyState===1)return;ws=new WebSocket("ws://127.0.0.1:$wsPort");ws.binaryType="arraybuffer";ws.onmessage=(e)=>{if(w.length>0){w.shift()(e.data);}else{q.push(e.data);}};return new Promise((res,rej)=>{ws.onopen=res;ws.onerror=rej;});},selectConfiguration:async()=>{},claimInterface:async()=>{iface.claimed=true;},selectAlternateInterface:async()=>{},releaseInterface:async()=>{},clearHalt:async()=>{},reset:async()=>{},close:async()=>{fakeDev.opened=false;if(ws)ws.close();},controlTransferIn:async()=>({data:new DataView(new ArrayBuffer(0)),status:"ok"}),controlTransferOut:async()=>({bytesWritten:0,status:"ok"}),transferOut:async(ep,data)=>{let buf=data instanceof ArrayBuffer?data:new Uint8Array(data.buffer,data.byteOffset,data.byteLength);ws.send(buf);return{bytesWritten:buf.byteLength,status:"ok"};},transferIn:async(ep,len)=>{let buf=q.length>0?q.shift():await new Promise(r=>w.push(r));return{data:new DataView(buf),status:"ok"};}};Object.defineProperty(navigator,"usb",{value:{getDevices:async()=>[fakeDev],requestDevice:async()=>fakeDev,addEventListener:()=>{},removeEventListener:()=>{}},configurable:true});alert("WebUSB v2 Aktif!");})();"""
}
