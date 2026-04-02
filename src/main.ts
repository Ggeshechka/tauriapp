import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";

let statusMsgEl: HTMLElement | null;

function updateUi(isRunning: boolean) {
  if (statusMsgEl) {
    statusMsgEl.textContent = isRunning ? "RUNNING" : "STOPPED";
    statusMsgEl.style.color = isRunning ? "#00ff00" : "#ff4444";
  }
}

async function sendVpnCommand(action: string) {
  try {
    const res: any = await invoke("plugin:xray|ping", {
      payload: { value: JSON.stringify({ action: action }) }
    });
    
    const data = JSON.parse(res.value || "{}");
    
    if (action === "status") {
      updateUi(data.running);
    } else if (action === "start") {
      updateUi(true);
    } else if (action === "stop") {
      updateUi(false);
    }
  } catch (error) {
    console.error("VPN Error:", error);
  }
}

window.addEventListener("DOMContentLoaded", async () => {
  statusMsgEl = document.querySelector("#status-msg");
  
  document.querySelector("#btn-start")?.addEventListener("click", () => sendVpnCommand("start"));
  document.querySelector("#btn-stop")?.addEventListener("click", () => sendVpnCommand("stop"));
  document.querySelector("#btn-status")?.addEventListener("click", () => sendVpnCommand("status"));

  sendVpnCommand("status");

  await listen<any>("vpn_state_changed", (event) => {
    updateUi(event.payload.running);
  });

  await listen<any>("plugin:xray:vpn_state_changed", (event) => {
    updateUi(event.payload.running);
  });

  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") {
      sendVpnCommand("status");
    }
  });

  window.addEventListener("focus", () => {
    sendVpnCommand("status");
  });

  // Отладочный таймер
  let counter = 0;
  const timerEl = document.createElement("div");
  timerEl.style.fontSize = "120px";
  timerEl.style.fontWeight = "bold";
  timerEl.style.color = "#007bff";
  timerEl.style.position = "absolute";
  timerEl.style.top = "50%";
  timerEl.style.left = "50%";
  timerEl.style.transform = "translate(-50%, -50%)";
  timerEl.style.zIndex = "9999";
  document.body.appendChild(timerEl);

  setInterval(() => {
    counter++;
    timerEl.textContent = counter.toString();
    console.log("Timer: ", counter);
  }, 
              1000);
});
