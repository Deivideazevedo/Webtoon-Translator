const { invoke } = window.__TAURI__.core;
const sel = document.getElementById("sel");

let startX = 0;
let startY = 0;
let dragging = false;

document.addEventListener("mousedown", (e) => {
  dragging = true;
  startX = e.clientX;
  startY = e.clientY;
  sel.style.left = startX + "px";
  sel.style.top = startY + "px";
  sel.style.width = "0px";
  sel.style.height = "0px";
  sel.style.display = "block";
});

document.addEventListener("mousemove", (e) => {
  if (!dragging) return;
  const x = Math.min(e.clientX, startX);
  const y = Math.min(e.clientY, startY);
  const w = Math.abs(e.clientX - startX);
  const h = Math.abs(e.clientY - startY);
  sel.style.left = x + "px";
  sel.style.top = y + "px";
  sel.style.width = w + "px";
  sel.style.height = h + "px";
});

document.addEventListener("mouseup", (e) => {
  if (!dragging) return;
  dragging = false;
  const x = Math.round(Math.min(e.clientX, startX));
  const y = Math.round(Math.min(e.clientY, startY));
  const width = Math.round(Math.abs(e.clientX - startX));
  const height = Math.round(Math.abs(e.clientY - startY));

  if (width < 4 || height < 4) {
    invoke("close_window");
    return;
  }
  invoke("region_selected", { x, y, width, height }).catch((err) => {
    console.error("Falha ao processar captura:", err);
  });
});

document.addEventListener("keydown", (e) => {
  if (e.key === "Escape") {
    invoke("close_window");
  }
});
