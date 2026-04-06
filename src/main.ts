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

function showMessage(msg: string, isError = true) {
  const errorMsg = document.getElementById("error-msg");
  if (errorMsg) {
    errorMsg.style.color = isError ? "#ff4444" : "#00C853";
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
      showMessage("Ошибка запуска ядра");
    }
  } catch (error: any) {
    updateUi(isRunning, false);
    showMessage("Системная ошибка: " + String(error));
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

async function loadApps() {
  try {
    const res = await invoke<any>("plugin:xray|ping", {
      payload: { value: JSON.stringify({ action: "get_apps" }) }
    });
    const data = JSON.parse(res.value || "{}");
    const textarea = document.getElementById("bypass-apps") as HTMLTextAreaElement;
    if (textarea && data.apps) {
      textarea.value = data.apps.join("\n");
    }
  } catch (e) {}
}

async function saveApps() {
  const textarea = document.getElementById("bypass-apps") as HTMLTextAreaElement;
  if (!textarea) return;
  
  const apps = textarea.value.split("\n").map(s => s.trim()).filter(s => s.length > 0);
  try {
    await invoke<any>("plugin:xray|ping", {
      payload: { value: JSON.stringify({ action: "save_apps", apps }) }
    });
    showMessage("Исключения сохранены", false);
  } catch (e) {
    showMessage("Ошибка сохранения");
  }
}

window.addEventListener("DOMContentLoaded", () => {
  const btn = document.getElementById("vpn-btn");
  if (btn) btn.addEventListener("click", toggleVpn);
  
  const saveBtn = document.getElementById("save-apps-btn");
  if (saveBtn) saveBtn.addEventListener("click", saveApps);
  
  checkStatus();
  loadApps();

  window.addEventListener("native_vpn_update", (e: any) => {
    if (!isProcessing && e?.detail) {
      updateUi(e.detail.running, false);
    }
  });

  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") checkStatus();
  });

  window.addEventListener("focus", checkStatus);
});