import { invoke } from "@tauri-apps/api/core";

let statusMsgEl: HTMLElement | null;

async function sendVpnCommand(action: string) {
  try {
    const res: any = await invoke("plugin:xray|ping", {
      value: JSON.stringify({ action: action })
    });
    
    if (action === "status") {
      if (statusMsgEl) statusMsgEl.textContent = res.running ? "RUNNING" : "STOPPED";
    } else {
      // Обновляем статус после нажатия Start/Stop
      checkStatus();
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

  // Проверка статуса при загрузке приложения
  checkStatus();
});