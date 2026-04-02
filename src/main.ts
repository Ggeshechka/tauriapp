import { invoke } from "@tauri-apps/api/core";

const btn = document.getElementById("vpn-btn") as HTMLButtonElement;
const btnText = document.getElementById("btn-text") as HTMLSpanElement;
const spinner = document.getElementById("spinner") as HTMLDivElement;
const errorMsg = document.getElementById("error-msg") as HTMLDivElement;

let isRunning = false;
let isProcessing = false;

function updateUi(running: boolean, processing: boolean) {
  isRunning = running;
  isProcessing = processing;

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
  errorMsg.textContent = msg;
  setTimeout(() => { errorMsg.textContent = ""; }, 4000);
}

async function toggleVpn() {
  if (isProcessing) return; // Защита от мисклика и спама

  const targetAction = isRunning ? "stop" : "start";
  updateUi(isRunning, true);

  try {
    const res: any = await invoke("plugin:xray|ping", {
      payload: { value: JSON.stringify({ action: targetAction }) }
    });
    
    const data = JSON.parse(res.value || "{}");
    
    // Искусственная минимальная задержка (500мс) чтобы юзер увидел статус загрузки
    await new Promise(r => setTimeout(r, 500));

    if (data.success) {
      updateUi(targetAction === "start", false);
    } else {
      updateUi(isRunning, false);
      showError("Ошибка запуска ядра");
    }
  } catch (error: any) {
    updateUi(isRunning, false);
    showError("Системная ошибка: " + error.toString());
  }
}

async function checkStatus() {
  if (isProcessing) return;
  
  try {
    const res: any = await invoke("plugin:xray|ping", {
      payload: { value: JSON.stringify({ action: "status" }) }
    });
    const data = JSON.parse(res.value || "{}");
    updateUi(data.running, false);
  } catch (error) {
    console.error("Status error:", error);
  }
}

window.addEventListener("DOMContentLoaded", () => {
  btn.addEventListener("click", toggleVpn);
  checkStatus();

  // Прямое обновление из шторки (если юзер переключил не через кнопку)
  window.addEventListener("native_vpn_update", ((e: CustomEvent) => {
    if (!isProcessing) {
      updateUi(e.detail.running, false);
    }
  }) as EventListener);

  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") checkStatus();
  });

  window.addEventListener("focus", che
                          ckStatus);
});
