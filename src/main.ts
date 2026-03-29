import { invoke } from "@tauri-apps/api/core";

let statusMsgEl: HTMLElement | null;

async function sendVpnCommand(action: string) {
  try {
    // В Tauri 2.0 данные для плагина нужно оборачивать в payload
    const res: any = await invoke("plugin:xray|ping", {
      payload: {
        value: JSON.stringify({ action: action })
      }
    });
    
    if (action === "status") {
      if (statusMsgEl) {
        statusMsgEl.textContent = res.running ? "RUNNING" : "STOPPED";
        statusMsgEl.style.color = res.running ? "#00ff00" : "#ff4444";
      }
    } else {
      // Если нажали Start или Stop, запрашиваем актуальный статус
      setTimeout(checkStatus, 500); 
    }
  } catch (error) {
    console.error("VPN Error:", error);
  }
}

function checkStatus() {
  sendVpnCommand("status");
}

window.addEventListener("DOMContentLoaded", () => {
  statusMsgEl = document.querySelector("#status-msg");
  
  document.querySelector("#btn-start")?.addEventListener("click", () => sendVpnCommand("start"));
  document.querySelector("#btn-stop")?.addEventListener("click", () => sendVpnCommand("stop"));

  // Проверяем статус при загрузке
  checkStatus();
});