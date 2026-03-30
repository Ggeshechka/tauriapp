import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event"; // Добавлен импорт

let statusMsgEl: HTMLElement | null;

async function sendVpnCommand(action: string) {
  try {
    const res: any = await invoke("plugin:xray|ping", {
      payload: { value: JSON.stringify({ action: action }) }
    });
    
    const data = JSON.parse(res.value || "{}");
    
    // Ручное обновление статуса нужно только для первоначальной проверки
    if (action === "status") {
      updateUi(data.running);
    }
  } catch (error) {
    console.error("VPN Error:", error);
    if (statusMsgEl) {
        statusMsgEl.textContent = "ERROR";
        statusMsgEl.style.color = "#ffcc00";
    }
  }
}

// Отдельная функция для обновления интерфейса
function updateUi(isRunning: boolean) {
  if (statusMsgEl) {
    statusMsgEl.textContent = isRunning ? "RUNNING" : "STOPPED";
    statusMsgEl.style.color = isRunning ? "#00ff00" : "#ff4444";
  }
}

window.addEventListener("DOMContentLoaded", async () => {
  statusMsgEl = document.querySelector("#status-msg");
  
  document.querySelector("#btn-start")?.addEventListener("click", () => sendVpnCommand("start"));
  document.querySelector("#btn-stop")?.addEventListener("click", () => sendVpnCommand("stop"));
  document.querySelector("#btn-status")?.addEventListener("click", () => sendVpnCommand("status"));

  // 1. Проверяем статус при запуске (вдруг VPN уже работал в фоне)
  sendVpnCommand("status");

  // 2. Моментально ловим события из шторки или сервиса
  await listen<{ running: boolean }>("vpn_state_changed", (event) => {
    updateUi(event.payload.running);
  })
    ;
});
