const editor = document.getElementById("editor");
const statusEl = document.getElementById("status");
const pathEl = document.getElementById("config-path");

function setStatus(msg, type) {
  statusEl.textContent = msg;
  statusEl.className = "status" + (type ? " " + type : "");
}

async function loadConfig() {
  setStatus("正在加载…");
  const res = await fetch("/api/config");
  const data = await res.json();
  if (!res.ok) {
    setStatus(data.error || "加载失败", "err");
    return;
  }
  pathEl.textContent = "配置文件：" + data.path;
  editor.value = JSON.stringify(data.config, null, 2);
  setStatus("已加载", "ok");
}

async function saveConfig() {
  let config;
  try {
    config = JSON.parse(editor.value);
  } catch (e) {
    setStatus("JSON 格式错误：" + e.message, "err");
    return;
  }
  setStatus("正在保存…");
  const res = await fetch("/api/config", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ config }),
  });
  const data = await res.json();
  if (!res.ok) {
    setStatus(data.error || "保存失败", "err");
    return;
  }
  pathEl.textContent = "配置文件：" + data.path;
  setStatus("保存成功，请重启应用使签到服务生效", "ok");
}

document.getElementById("btn-reload").addEventListener("click", loadConfig);
document.getElementById("btn-format").addEventListener("click", () => {
  try {
    editor.value = JSON.stringify(JSON.parse(editor.value), null, 2);
    setStatus("已格式化", "ok");
  } catch (e) {
    setStatus("JSON 格式错误：" + e.message, "err");
  }
});
document.getElementById("btn-save").addEventListener("click", saveConfig);

loadConfig().catch((e) => setStatus(String(e), "err"));
