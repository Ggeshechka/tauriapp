import { invoke } from "@tauri-apps/api/core";

let statusMsgEl: HTMLElement | null;

async function sendVpnCommand(action: string) {
  try {
    const res: any = await invoke("plugin:xray|ping", {
      payload: {
        value: JSON.stringify({ action: action })
      }
    });
    
    // Распаковываем ответ из строки, которую вернул Kotlin -> Rust -> TS
    const data = JSON.parse(res.value || "{}");
    
    if (action === "status") {
      if (statusMsgEl) {
        statusMsgEl.textContent = data.running ? "RUNNING" : "STOPPED";
        statusMsgEl.style.color = data.running ? "#00ff00" : "#ff4444";
      }
    } else {
      setTimeout(checkStatus, 500); 
    }
  } catch (error) {
    console.error("VPN Error:", error);
    if (statusMsgEl) {
        statusMsgEl.textContent = "ERROR: " + error;
        statusMsgEl.style.color = "#ffcc00";
    }
  }
}

function checkStatus() {
  sendVpnCommand("status");
}

window.addEventListener("DOMContentLoaded", () => {
  statusMsgEl = document.querySelector("#status-msg");
  
  document.querySelector("#btn-start")?.addEventListener("click", () => sendVpnCommand("start"));
  document.querySelector("#btn-stop")?.addEventListener("click", () => sendVpnCommand("stop"));
  document.querySelector("#btn-status")?.addEventListener("click", checkStatus);

  checkStatus();
});