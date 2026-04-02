import { invoke } from "@tauri-apps/api/core";

let isRunning = false;
let isProcessing = false;

function updateUi(running: boolean, processing: boolean) {
  isRunning = running;
  isProcessing = processing;

  const btn = document.getElementById("vpn-btn") as HTMLButtonElement | null;
  const btnText = document.getElementById("btn-text");
  const spinner = document.getElementById("spinner");
  const errorMsg = document.getElementById("error-msg");

  if (!btn || !btnText || !spinner || !errorMsg) return;

  btn.disabled = isProcessing;

  if (isProcessing) {
    btn.className = "state-loading";
    btnText.classList.add("hidden");
    spinner.classList.remove("hidden");
    errorMsg.textContent = "";
  } else {
    spinner.classList.add("hidden");
    btnText.classList.remove("hidden");
    if (isRunning) {
      btn.className = "state-running";
      btnText.textContent = "STOP";
    } else {
      btn.className = "state-stopped";
      btnText.textContent = "START";
    }
  }
}

function showError(msg: string) {
  const errorMsg = document.getElementById("error-msg");
  if (errorMsg) {
    errorMsg.textContent = msg;
    setTimeout(() => { errorMsg.textContent = ""; }, 4000);
  }
}

async function toggleVpn() {
  if (isProcessing) return;

  const targetAction = isRunning ? "stop" : "start";
  updateUi(isRunning, true);

  try {
    const res = await invoke<any>("plugin:xray|ping", {
      payload: { value: JSON.stringify({ action: targetAction }) }
    });
    
    const data = JSON.parse(res.value || "{}");
    
    await new Promise(r => setTimeout(r, 500));

    if (data.success) {
      updateUi(targetAction === "start", false);
    } else {
      updateUi(isRunning, false);
      showError("Ошибка запуска ядра");
    }
  } catch (error: any) {
    updateUi(isRunning, false);
    showError("Системная ошибка: " + String(error));
  }
}

async function checkStatus() {
  if (isProcessing) return;
  
  try {
    const res = await invoke<any>("plugin:xray|ping", {
      payload: { value: JSON.stringify({ action: "status" }) }
    });
    const data = JSON.parse(res.value || "{}");
    updateUi(data.running, false);
  } catch (error) {
    console.error("Status error:", error);
  }
}

window.addEventListener("DOMContentLoaded", () => {
  const btn = document.getElementById("vpn-btn");
  if (btn) btn.addEventListener("click", toggleVpn);
  
  checkStatus();

  window.addEventListener("native_vpn_update", (e: any) => {
    if (!isProcessing && e?.detail) {
      updateUi(e.detail.running, false);
    }
  });

  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") checkStatus();
  });

  window.addEventListener("focus", ch
                          eckStatus);
});
