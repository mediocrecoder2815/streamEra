/*
 * StreamEra demo frontend.
 * Plain browser JS with hash routing. Everything talks to the Spring GraphQL
 * endpoint at /graphql. The JWT is kept in localStorage and sent as a Bearer
 * token, matching JwtAuthFilter on the backend.
 */

const API_URL = "/graphql";
const LS_TOKEN = "streamera.token";
const LS_USER = "streamera.username";
const MAX_UPLOAD_BYTES = 2_000_000_000; // 2 GB decimal, matches the backend limit
const UPLOAD_POLL_ATTEMPTS = 90; // 90 * 2s = 3 minutes
const UPLOAD_POLL_INTERVAL_MS = 2000;

const viewEl = document.getElementById("view");

const state = {
  token: localStorage.getItem(LS_TOKEN) || "",
  username: localStorage.getItem(LS_USER) || "",
  videos: [],
  catalogLoaded: false,
  catalogError: "",
  search: "",
  focusSearch: false,
  hls: null,
};

let generation = 0;

/* ------------------------------------------------------------------ */
/* Helpers                                                            */
/* ------------------------------------------------------------------ */

function esc(value) {
  return String(value ?? "").replace(/[&<>"']/g, (char) => {
    switch (char) {
      case "&": return "&amp;";
      case "<": return "&lt;";
      case ">": return "&gt;";
      case '"': return "&quot;";
      default: return "&#39;";
    }
  });
}

function hashString(value) {
  const text = String(value ?? "");
  let hash = 0;
  for (let i = 0; i < text.length; i += 1) {
    hash = (hash * 31 + text.charCodeAt(i)) >>> 0;
  }
  return hash;
}

function posterVars(name) {
  const hash = hashString(name);
  return `--h1:${hash % 360};--h2:${(hash * 7) % 360};`;
}

function initials(name) {
  return String(name || "?")
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => word[0])
    .join("")
    .toUpperCase();
}

function categoryNames(video) {
  return (video?.categorySet || [])
    .map((category) => category?.name)
    .filter(Boolean);
}

function formatDuration(seconds) {
  if (!seconds || Number(seconds) <= 0) return "—";
  const total = Math.round(Number(seconds));
  const hours = Math.floor(total / 3600);
  const minutes = Math.round((total % 3600) / 60);
  if (hours > 0) return `${hours}h ${minutes}m`;
  return `${minutes}m`;
}

function formatClock(seconds) {
  const total = Math.max(0, Math.floor(Number(seconds) || 0));
  const minutes = Math.floor(total / 60);
  const rest = total % 60;
  return `${minutes}:${String(rest).padStart(2, "0")}`;
}

function formatSize(bytes) {
  if (!bytes || Number(bytes) <= 0) return "—";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let value = Number(bytes);
  let index = 0;
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024;
    index += 1;
  }
  return `${value.toFixed(value >= 10 || index === 0 ? 0 : 1)} ${units[index]}`;
}

function formatDate(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value ?? "—");
  return date.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

function synopsis(video) {
  const genres = categoryNames(video);
  const genre = genres.length ? genres.join(" · ") : "StreamEra original";
  return `${genre} — a demo title from the StreamEra catalog. Sign in to keep your watch history and playback progress in sync.`;
}

function videoStatusLabel(status) {
  const key = String(status || "").toUpperCase();
  return {
    PENDING: "Processing",
    PROCESSING: "Processing",
    OK: "Ready",
    ERROR: "Failed",
    DELETED: "Deleted",
  }[key] || key || "Unknown";
}

/* ------------------------------------------------------------------ */
/* GraphQL                                                            */
/* ------------------------------------------------------------------ */

async function gql(query, variables = {}) {
  let response;
  try {
    response = await fetch(API_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(state.token ? { Authorization: `Bearer ${state.token}` } : {}),
      },
      body: JSON.stringify({ query, variables }),
    });
  } catch (error) {
    throw new Error("Cannot reach the StreamEra API. Is the backend running?");
  }

  const text = await response.text();
  let payload;
  try {
    payload = JSON.parse(text);
  } catch (error) {
    throw new Error(`Unexpected response from the API (HTTP ${response.status}).`);
  }

  if (Array.isArray(payload.errors) && payload.errors.length > 0) {
    throw new Error(payload.errors[0]?.message || "GraphQL request failed.");
  }
  if (!response.ok) {
    throw new Error(`Request failed (HTTP ${response.status}).`);
  }
  return payload.data || {};
}

/* ------------------------------------------------------------------ */
/* Session                                                            */
/* ------------------------------------------------------------------ */

function setSession(token, username) {
  state.token = token;
  state.username = username;
  localStorage.setItem(LS_TOKEN, token);
  localStorage.setItem(LS_USER, username);
}

function clearSession() {
  state.token = "";
  state.username = "";
  localStorage.removeItem(LS_TOKEN);
  localStorage.removeItem(LS_USER);
}

function signOut() {
  clearSession();
  renderAuthZone();
  toast("Signed out.", "info");
  if (location.hash && location.hash !== "#/") {
    location.hash = "#/";
  } else {
    renderRoute();
  }
}

/* ------------------------------------------------------------------ */
/* Header                                                             */
/* ------------------------------------------------------------------ */

function renderHeader() {
  const header = document.getElementById("topbar");
  header.innerHTML = `
    <div class="topbar-inner">
      <a class="brand" href="#/" aria-label="StreamEra home">
        <span class="brand-mark">▶</span>
        <span>Stream<em class="accent">Era</em></span>
      </a>
      <nav class="nav" aria-label="Primary">
        <a class="nav-link" data-nav="browse" href="#/">Browse</a>
        <a class="nav-link" data-nav="history" href="#/history">Watch history</a>
        <a class="nav-link" data-nav="upload" href="#/upload">Upload</a>
      </nav>
      <div class="topbar-tools">
        <label class="search-wrap">
          <span class="search-icon" aria-hidden="true">⌕</span>
          <input id="search" class="search" type="search" placeholder="Search titles" autocomplete="off" aria-label="Search titles">
        </label>
        <div id="auth-zone" class="auth-zone"></div>
      </div>
    </div>
  `;

  const search = header.querySelector("#search");
  search.value = state.search;
  search.addEventListener("input", () => {
    state.search = search.value.trim().toLowerCase();
    const onBrowse = !location.hash || location.hash === "#/" || location.hash === "#";
    if (onBrowse) {
      applyCatalogFilter();
    } else {
      state.focusSearch = true;
      location.hash = "#/";
    }
  });

  renderAuthZone();
}

function renderAuthZone() {
  const zone = document.getElementById("auth-zone");
  if (!zone) return;

  if (state.token) {
    zone.innerHTML = `
      <button class="user-chip" id="user-chip" title="Signed in as ${esc(state.username)}">
        <span class="avatar">${esc(initials(state.username))}</span>
        <span class="user-name">${esc(state.username)}</span>
      </button>
      <button class="btn ghost small" id="sign-out">Sign out</button>
    `;
    const chip = zone.querySelector("#user-chip");
    const signOutBtn = zone.querySelector("#sign-out");
    if (chip) chip.addEventListener("click", () => { location.hash = "#/history"; });
    if (signOutBtn) signOutBtn.addEventListener("click", signOut);
  } else {
    zone.innerHTML = `
      <button class="btn ghost small" data-open-auth="login">Sign in</button>
      <button class="btn primary small" data-open-auth="register">Create account</button>
    `;
  }
}

function setActiveNav(active) {
  document.querySelectorAll("[data-nav]").forEach((link) => {
    link.classList.toggle("active", link.dataset.nav === active);
  });
}

/* ------------------------------------------------------------------ */
/* Toasts / modal / loading                                           */
/* ------------------------------------------------------------------ */

function toast(message, tone = "info") {
  const root = document.getElementById("toasts");
  if (!root) return;
  const item = document.createElement("div");
  item.className = `toast ${tone}`;
  item.textContent = message;
  root.appendChild(item);
  requestAnimationFrame(() => item.classList.add("show"));
  setTimeout(() => {
    item.classList.remove("show");
    setTimeout(() => item.remove(), 250);
  }, 3600);
}

function closeModal() {
  const root = document.getElementById("modal-root");
  if (root) root.innerHTML = "";
  document.body.classList.remove("modal-open");
}

function openAuth(mode = "login") {
  const root = document.getElementById("modal-root");
  root.innerHTML = `
    <div class="modal-backdrop" id="auth-backdrop">
      <div class="modal" role="dialog" aria-modal="true" aria-labelledby="auth-title">
        <button class="icon-btn modal-close" type="button" data-close-modal aria-label="Close">×</button>
        <div class="modal-brand">
          <span class="brand-mark">▶</span>
          <span>Stream<em class="accent">Era</em></span>
        </div>
        <h2 id="auth-title">Welcome back</h2>
        <p class="muted modal-sub">Sign in to sync your watch history and playback progress.</p>
        <div class="tabs" role="tablist">
          <button class="tab" type="button" data-mode="login">Sign in</button>
          <button class="tab" type="button" data-mode="register">Create account</button>
        </div>
        <form id="auth-form" class="auth-form">
          <label class="field">
            <span>Username</span>
            <input name="username" autocomplete="username" required maxlength="42" placeholder="alice">
          </label>
          <label class="field">
            <span>Password</span>
            <input name="password" type="password" autocomplete="current-password" required placeholder="••••••••">
          </label>
          <p class="form-error hidden" id="auth-error"></p>
          <button class="btn primary block" type="submit" id="auth-submit">Sign in</button>
        </form>
        <p class="muted small modal-hint" id="auth-hint">Demo account: alice / password — or create a new one.</p>
      </div>
    </div>
  `;

  const backdrop = document.getElementById("auth-backdrop");
  const form = document.getElementById("auth-form");
  const title = document.getElementById("auth-title");
  const submit = document.getElementById("auth-submit");
  const errorBox = document.getElementById("auth-error");
  const hint = document.getElementById("auth-hint");
  const passwordInput = form.querySelector('input[name="password"]');
  let currentMode = mode === "register" ? "register" : "login";

  const applyMode = (nextMode) => {
    currentMode = nextMode;
    root.querySelectorAll("[data-mode]").forEach((tab) => {
      tab.classList.toggle("active", tab.dataset.mode === currentMode);
    });
    title.textContent = currentMode === "login" ? "Welcome back" : "Create your account";
    submit.textContent = currentMode === "login" ? "Sign in" : "Create account";
    passwordInput.autocomplete = currentMode === "login" ? "current-password" : "new-password";
    hint.textContent = currentMode === "login"
      ? "Demo account: alice / password — or create a new one."
      : "New accounts are created with the standard USER role.";
    errorBox.classList.add("hidden");
  };

  root.querySelectorAll("[data-mode]").forEach((tab) => {
    tab.addEventListener("click", () => applyMode(tab.dataset.mode));
  });

  backdrop.addEventListener("click", (event) => {
    if (event.target === backdrop) closeModal();
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const data = new FormData(form);
    const username = String(data.get("username") || "").trim();
    const password = String(data.get("password") || "");

    if (!username || !password) {
      errorBox.textContent = "Username and password are required.";
      errorBox.classList.remove("hidden");
      return;
    }

    submit.disabled = true;
    submit.textContent = currentMode === "login" ? "Signing in…" : "Creating account…";
    errorBox.classList.add("hidden");

    try {
      const mutation = currentMode === "login"
        ? `mutation($request: AuthInput!) { login(requestDto: $request) { token username } }`
        : `mutation($request: AuthInput!) { register(requestDto: $request) { token username } }`;
      const result = await gql(mutation, { request: { username, password } });
      const session = currentMode === "login" ? result.login : result.register;
      if (!session?.token) throw new Error("Authentication failed.");

      setSession(session.token, session.username);
      closeModal();
      renderAuthZone();
      toast(currentMode === "login"
        ? `Welcome back, ${session.username}.`
        : `Account created — welcome, ${session.username}.`, "success");
      renderRoute();
    } catch (error) {
      errorBox.textContent = error.message;
      errorBox.classList.remove("hidden");
    } finally {
      submit.disabled = false;
      submit.textContent = currentMode === "login" ? "Sign in" : "Create account";
    }
  });

  applyMode(currentMode);
  document.body.classList.add("modal-open");
  setTimeout(() => form.querySelector('input[name="username"]')?.focus(), 30);
}

function renderLoading(message = "Loading…") {
  viewEl.innerHTML = `<div class="loading"><span class="spinner" aria-hidden="true"></span><span>${esc(message)}</span></div>`;
}

function errorState(title, message, retryId) {
  return `
    <section class="empty-state">
      <span class="empty-icon">⚠️</span>
      <h1>${esc(title)}</h1>
      <p>${esc(message)}</p>
      ${retryId ? `<button class="btn primary" id="${retryId}">Try again</button>` : ""}
    </section>
  `;
}

/* ------------------------------------------------------------------ */
/* Upload                                                             */
/* ------------------------------------------------------------------ */

function isMp4File(file) {
  return file.type === "video/mp4" || /\.mp4$/i.test(file.name || "");
}

function putFile(url, file, contentType, onProgress) {
  return new Promise((resolve, reject) => {
    const request = new XMLHttpRequest();
    request.open("PUT", url);
    request.setRequestHeader("Content-Type", contentType);

    request.upload.addEventListener("progress", (event) => {
      if (event.lengthComputable) onProgress(event.loaded / event.total);
    });

    request.addEventListener("load", () => {
      if (request.status >= 200 && request.status < 300) {
        resolve();
      } else {
        reject(new Error(`Storage upload failed (HTTP ${request.status}). Check the MinIO/S3 CORS configuration.`));
      }
    });
    request.addEventListener("error", () => {
      reject(new Error("Storage upload failed. Check that the presigned URL is reachable and CORS allows PUT."));
    });
    request.addEventListener("abort", () => reject(new Error("Upload cancelled.")));

    request.send(file);
  });
}

async function pollVideoStatus(videoId, gen, onUpdate) {
  for (let attempt = 0; attempt < UPLOAD_POLL_ATTEMPTS; attempt += 1) {
    if (gen !== generation) return "cancelled";

    try {
      const data = await gql(
        `query($id: Long!) { video_status(videoId: $id) }`,
        { id: videoId },
      );
      const status = String(data.video_status || "").toUpperCase();
      if (status === "OK") return "OK";
      if (status === "ERROR" || status === "DELETED") return status;
      onUpdate(`Processing… ${Math.round(((attempt + 1) * UPLOAD_POLL_INTERVAL_MS) / 1000)}s`);
    } catch (error) {
      if (attempt === 0) onUpdate("Waiting for the processing worker…");
    }

    await new Promise((resolve) => setTimeout(resolve, UPLOAD_POLL_INTERVAL_MS));
  }
  return "TIMEOUT";
}

async function renderUpload(gen) {
  if (!state.token) {
    viewEl.innerHTML = `
      <section class="empty-state">
        <span class="empty-icon">⬆️</span>
        <h1>Upload a video</h1>
        <p>Sign in before uploading so the catalog can attribute the title to your account.</p>
        <button class="btn primary" data-open-auth="login">Sign in</button>
      </section>
    `;
    return;
  }

  viewEl.innerHTML = `
    <div class="page-head">
      <div>
        <p class="eyebrow">Creator tools</p>
        <h1>Upload a video</h1>
        <p class="muted">MP4 only, up to 2 GB. The worker converts it to HLS before it appears in playback.</p>
      </div>
      <a class="btn ghost small" href="#/">Back to catalog</a>
    </div>

    <section class="upload-panel">
      <form id="upload-form" class="auth-form">
        <label class="field">
          <span>Title</span>
          <input name="title" id="upload-title" required maxlength="120" placeholder="My first stream">
        </label>
        <label class="field">
          <span>Video file</span>
          <input name="file" id="upload-file" type="file" accept="video/mp4,.mp4" required>
        </label>
        <p class="hint" id="upload-file-meta">Choose an MP4 file.</p>
        <div class="upload-meter hidden" id="upload-meter" aria-hidden="true"><span></span></div>
        <p class="form-error hidden" id="upload-error"></p>
        <p class="notice hidden" id="upload-status"></p>
        <div class="upload-actions">
          <button class="btn primary" type="submit" id="upload-submit">Upload &amp; process</button>
          <span class="hint" id="upload-note">The file goes directly to object storage; the server only signs the request.</span>
        </div>
      </form>
    </section>
  `;

  const form = document.getElementById("upload-form");
  const titleInput = document.getElementById("upload-title");
  const fileInput = document.getElementById("upload-file");
  const fileMeta = document.getElementById("upload-file-meta");
  const errorBox = document.getElementById("upload-error");
  const statusBox = document.getElementById("upload-status");
  const meter = document.getElementById("upload-meter");
  const meterFill = meter.querySelector("span");
  const submit = document.getElementById("upload-submit");
  const note = document.getElementById("upload-note");
  let busy = false;

  const showError = (message) => {
    errorBox.textContent = message;
    errorBox.classList.remove("hidden");
  };

  const resetFeedback = () => {
    errorBox.classList.add("hidden");
    statusBox.classList.add("hidden");
    meter.classList.add("hidden");
    meterFill.style.width = "0%";
  };

  const showStatus = (message) => {
    statusBox.textContent = message;
    statusBox.classList.remove("hidden");
  };

  const updateFileMeta = () => {
    const file = fileInput.files?.[0];
    if (!file) {
      fileMeta.textContent = "Choose an MP4 file.";
      return;
    }
    fileMeta.textContent = `${file.name} · ${formatSize(file.size)}`;
    if (!titleInput.value.trim()) {
      titleInput.value = file.name.replace(/\.[^.]+$/, "");
    }
  };

  fileInput.addEventListener("change", updateFileMeta);

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (busy) return;

    resetFeedback();
    const title = titleInput.value.trim();
    const file = fileInput.files?.[0];

    if (!title) {
      showError("Give the video a title first.");
      return;
    }
    if (!file) {
      showError("Choose an MP4 file to upload.");
      return;
    }
    if (!isMp4File(file)) {
      showError("Only MP4 files are supported right now.");
      return;
    }
    if (file.size > MAX_UPLOAD_BYTES) {
      showError(`That file is ${formatSize(file.size)}. The limit is ${formatSize(MAX_UPLOAD_BYTES)}.`);
      return;
    }

    busy = true;
    submit.disabled = true;
    submit.textContent = "Creating upload ticket…";
    note.textContent = "Preparing a direct upload to object storage.";

    try {
      const created = await gql(
        `mutation($video: VideoInput!) {
           add_video(video: $video) {
             videoId objectKey uploadUrl contentType expiresInSeconds
           }
         }`,
        { video: { name: title, size: file.size } },
      );
      const ticket = created.add_video;
      if (!ticket?.uploadUrl || ticket.videoId == null) {
        throw new Error("The backend did not return a usable upload ticket.");
      }

      showStatus("Uploading the video directly to storage…");
      submit.textContent = "Uploading…";
      meter.classList.remove("hidden");

      await putFile(
        ticket.uploadUrl,
        file,
        ticket.contentType || "video/mp4",
        (fraction) => {
          meterFill.style.width = `${Math.round(fraction * 100)}%`;
          submit.textContent = `Uploading… ${Math.round(fraction * 100)}%`;
        },
      );

      showStatus("Upload complete. Verifying the stored object…");
      submit.textContent = "Verifying…";
      note.textContent = "The server is checking the object type, size, and ETag.";

      const confirmed = await gql(
        `mutation($id: Long!) { confirm(videoId: $id) }`,
        { id: Number(ticket.videoId) },
      );
      if (String(confirmed.confirm || "").toUpperCase() !== "OK") {
        throw new Error("The backend rejected the uploaded file.");
      }

      showStatus("Queued for HLS processing. This can take a minute…");
      submit.textContent = "Processing…";
      note.textContent = "The worker is converting the file to HLS segments.";

      const status = await pollVideoStatus(ticket.videoId, gen, showStatus);
      if (gen !== generation) return;

      if (status === "OK") {
        state.catalogLoaded = false;
        toast("Video processed — opening it now.", "success");
        location.hash = `#/watch/${encodeURIComponent(title)}`;
        return;
      }
      if (status === "ERROR") {
        throw new Error("The worker could not process this file. Try a different MP4.");
      }
      if (status === "DELETED") {
        throw new Error("This video was deleted before processing finished.");
      }

      showStatus("Still processing. Open the catalog in a moment to check again.");
      submit.textContent = "Upload another";
      note.textContent = "The backend has not finished HLS conversion yet.";
      busy = false;
      submit.disabled = false;
      return;
    } catch (error) {
      showError(error.message);
      statusBox.classList.add("hidden");
    } finally {
      if (gen === generation) {
        busy = false;
        submit.disabled = false;
        if (submit.textContent !== "Upload another") submit.textContent = "Upload & process";
      }
    }
  });
}

/* ------------------------------------------------------------------ */
/* Catalog                                                            */
/* ------------------------------------------------------------------ */

async function loadCatalog() {
  try {
    const data = await gql(`query { videos { name videoLength videoStatus categorySet { categoryId name } } }`);
    state.videos = (data.videos || []).filter(Boolean);
    state.catalogError = "";
  } catch (error) {
    state.videos = [];
    state.catalogError = error.message;
  } finally {
    state.catalogLoaded = true;
  }
}

function cardMarkup(video) {
  const categories = categoryNames(video);
  const status = String(video.videoStatus || "").toUpperCase();
  const statusClass = status.toLowerCase();
  const searchText = `${video.name || ""} ${categories.join(" ")}`.toLowerCase();
  return `
    <article class="card" role="button" tabindex="0"
             data-card-name="${esc(video.name)}"
             data-search="${esc(searchText)}"
             aria-label="Open ${esc(video.name)}">
      <div class="poster" style="${posterVars(video.name)}">
        <span class="poster-initials">${esc(initials(video.name))}</span>
        ${status ? `<span class="status-badge ${esc(statusClass)}">${esc(status)}</span>` : ""}
        <span class="poster-duration">${formatDuration(video.videoLength)}</span>
        <span class="poster-play" aria-hidden="true">▶</span>
      </div>
      <div class="card-body">
        <h3>${esc(video.name)}</h3>
        <div class="chips">
          ${categories.slice(0, 2).map((name) => `<span class="chip">${esc(name)}</span>`).join("") || '<span class="chip">Uncategorized</span>'}
        </div>
      </div>
    </article>
  `;
}

function applyCatalogFilter() {
  const query = state.search;
  const cards = Array.from(viewEl.querySelectorAll("#catalog-grid [data-card-name]"));
  let shown = 0;

  cards.forEach((card) => {
    const matches = !query || (card.dataset.search || "").includes(query);
    card.classList.toggle("is-hidden", !matches);
    if (matches) shown += 1;
  });

  const empty = document.getElementById("empty-filter");
  if (empty) empty.classList.toggle("hidden", shown > 0);
}

async function renderBrowse(gen) {
  if (!state.catalogLoaded) {
    renderLoading("Loading catalog…");
    await loadCatalog();
    if (gen !== generation) return;
  }

  if (state.catalogError && state.videos.length === 0) {
    viewEl.innerHTML = errorState("The catalog could not be loaded", state.catalogError, "retry-catalog");
    return;
  }

  if (state.videos.length === 0) {
    viewEl.innerHTML = `
      <section class="empty-state">
        <span class="empty-icon">🎬</span>
        <h2>No titles yet</h2>
        <p>The catalog is empty. Upload an MP4 or seed the mock data, then refresh.</p>
        <a class="btn primary" href="#/upload">Upload a video</a>
      </section>
    `;
    return;
  }

  const featured = state.videos[0];
  const featuredCategories = categoryNames(featured);

  viewEl.innerHTML = `
    <section class="hero" style="${posterVars(featured.name)}">
      <div class="hero-scrim"></div>
      <div class="hero-content">
        <p class="eyebrow"><span class="dot"></span> Featured on StreamEra</p>
        <h1>${esc(featured.name)}</h1>
        <p class="hero-desc">${esc(synopsis(featured))}</p>
        <div class="meta-row">
          <span class="pill">${formatDuration(featured.videoLength)}</span>
          ${featuredCategories.slice(0, 3).map((name) => `<span class="pill subtle">${esc(name)}</span>`).join("")}
        </div>
        <div class="hero-actions">
          <button class="btn primary" data-card-name="${esc(featured.name)}">▶ Play</button>
          <button class="btn glass" data-card-name="${esc(featured.name)}">Details</button>
        </div>
      </div>
    </section>

    <section class="catalog-head">
      <div>
        <h2>All titles</h2>
        <p class="muted">${state.videos.length} title${state.videos.length === 1 ? "" : "s"} · served by the GraphQL catalog</p>
      </div>
      <span class="demo-chip">Catalog</span>
    </section>

    <section class="grid" id="catalog-grid">
      ${state.videos.map(cardMarkup).join("")}
    </section>

    <section class="empty-filter hidden" id="empty-filter">
      <h2>No matches</h2>
      <p class="muted">Try a different search term.</p>
    </section>
  `;

  applyCatalogFilter();
}

/* ------------------------------------------------------------------ */
/* Watch page                                                         */
/* ------------------------------------------------------------------ */

function destroyPlayer() {
  if (!state.hls) return;
  try { state.hls.destroy(); } catch (error) { /* ignore */ }
  state.hls = null;
}

function showStreamError(player, message) {
  const badge = document.getElementById("player-badge");
  destroyPlayer();
  if (badge) badge.textContent = "STREAM ERROR";
  player.removeAttribute("src");
  player.load();
  toast(message || "Stream unavailable. Reload the page and try again.", "error");
}

function attachSource(player, source) {
  if (!source) {
    showStreamError(player, "This title has no playback source yet.");
    return;
  }

  const isHls = /m3u8/i.test(source);
  let failed = false;

  const fail = () => {
    if (failed) return;
    failed = true;
    showStreamError(player);
  };

  player.addEventListener("error", fail);

  if (isHls && window.Hls && window.Hls.isSupported()) {
    try {
      const hls = new window.Hls({ enableWorker: true });
      state.hls = hls;
      hls.loadSource(source);
      hls.attachMedia(player);
      hls.on(window.Hls.Events.ERROR, (_event, data) => {
        if (data && data.fatal) fail();
      });
    } catch (error) {
      fail();
    }
    return;
  }

  // Safari plays HLS natively; other browsers need hls.js to be loaded.
  player.src = source;
}

function setProgressNote(message) {
  const note = document.getElementById("progress-note");
  if (note) note.textContent = message;
}

async function saveProgress(watchHistoryId, seconds, silent = false) {
  if (!watchHistoryId) return;
  try {
    await gql(
      `mutation($input: WatchHistoryProgressInput!) {
         update_watch_historyProgress(updateInput: $input) { watchHistoryId }
       }`,
      { input: { watchHistoryId, timestamp: Math.floor(seconds) } },
    );
    setProgressNote(`Progress saved at ${formatClock(seconds)}.`);
    if (!silent) toast("Progress saved.", "success");
  } catch (error) {
    setProgressNote(`Could not save progress: ${error.message}`);
    if (!silent) toast(error.message, "error");
  }
}

function setupPlayer(video, watchHistoryId, resumeAt = 0) {
  const player = document.getElementById("player");
  if (!player) return;

  attachSource(player, video.videoUrl);

  if (resumeAt > 0) {
    const resume = () => {
      const duration = Number(player.duration);
      if (Number.isFinite(duration) && duration > 0 && resumeAt >= duration - 2) return;
      try {
        player.currentTime = resumeAt;
        setProgressNote(`Resumed at ${formatClock(resumeAt)}.`);
      } catch (error) {
        /* seeking may not be available yet */
      }
    };
    if (player.readyState >= 1) {
      resume();
    } else {
      player.addEventListener("loadedmetadata", resume, { once: true });
    }
  }

  const saveButton = document.getElementById("save-progress");
  if (saveButton) {
    saveButton.addEventListener("click", () => saveProgress(watchHistoryId, player.currentTime));
  }

  if (!watchHistoryId) return;

  let lastSaved = Math.max(0, Number(resumeAt) || 0);
  player.addEventListener("timeupdate", () => {
    if (player.currentTime - lastSaved >= 20) {
      lastSaved = player.currentTime;
      saveProgress(watchHistoryId, player.currentTime, true);
    }
  });
  player.addEventListener("pause", () => {
    if (player.currentTime > 0) saveProgress(watchHistoryId, player.currentTime, true);
  });
}

function renderWatchLayout(video, options = {}) {
  const categories = categoryNames(video);
  const status = String(video.videoStatus || "").toUpperCase();
  const others = state.videos.filter((item) => item.name !== video.name).slice(0, 5);
  const watchHistoryId = options.watchHistoryId || null;
  const resumeAt = Number(options.resumeAt) || 0;
  const statusLabel = videoStatusLabel(status);

  let progressControls = "";
  if (!state.token) {
    progressControls = `<button class="btn primary" data-open-auth="login">Sign in to save progress</button>`;
  } else if (watchHistoryId) {
    progressControls = `<button class="btn primary" id="save-progress">Save progress</button>`;
  } else {
    progressControls = `<span class="hint">No watch-history row exists for this title yet, so progress can’t be saved.</span>`;
  }

  viewEl.innerHTML = `
    <section class="watch">
      <div class="watch-main">
        <a class="back-link" href="#/">← Back to catalog</a>
        ${options.notice ? `<div class="notice">${esc(options.notice)}</div>` : ""}
        <div class="player-wrap">
          <video id="player" controls playsinline preload="metadata"></video>
          <span class="player-badge" id="player-badge">${status === "OK" || !status ? "STREAM" : esc(status)}</span>
        </div>
        <h1>${esc(video.name)}</h1>
        <div class="meta-row">
          <span class="pill">${formatDuration(video.videoLength)}</span>
          <span class="pill subtle">${formatSize(video.size)}</span>
          <span class="pill subtle">${esc(statusLabel)}</span>
        </div>
        <p class="watch-desc">${esc(synopsis(video))}</p>
        <div class="watch-actions">
          ${progressControls}
          <span class="hint" id="progress-note"></span>
        </div>
      </div>

      <aside class="watch-side">
        <h2>Categories</h2>
        <div class="chips">
          ${categories.map((name) => `<span class="chip">${esc(name)}</span>`).join("") || '<span class="chip">Uncategorized</span>'}
        </div>
        <h2 style="margin-top:24px">More like this</h2>
        <div class="mini-list">
          ${others.map((item) => `
            <a class="mini-item" href="#/watch/${encodeURIComponent(item.name)}">
              <span class="poster" style="${posterVars(item.name)}">
                <span class="poster-initials">${esc(initials(item.name))}</span>
              </span>
              <span class="mini-copy">
                <span class="mini-title">${esc(item.name)}</span>
                <span class="mini-sub">${formatDuration(item.videoLength)} · ${esc(categoryNames(item)[0] || "Uncategorized")}</span>
              </span>
            </a>
          `).join("") || '<p class="muted small">No other titles in the catalog yet.</p>'}
        </div>
      </aside>
    </section>
  `;

  setupPlayer(video, watchHistoryId, resumeAt);
}

async function renderWatch(name, gen) {
  renderLoading("Loading title…");

  if (!state.catalogLoaded) {
    await loadCatalog();
    if (gen !== generation) return;
  }

  let video = null;
  let note = "";

  try {
    const data = await gql(
      `query($name: String) {
         video_byName(name: $name) {
           videoId name videoLength size videoUrl videoStatus
           categorySet { categoryId name }
         }
       }`,
      { name },
    );
    video = data.video_byName;
  } catch (error) {
    note = `The detail query failed: ${error.message}`;
  }

  if (gen !== generation) return;

  if (!video) {
    const known = state.videos.find((item) => item.name === name);
    viewEl.innerHTML = `
      <section class="empty-state">
        <span class="empty-icon">${known ? "⏳" : "🔎"}</span>
        <h1>${known ? "Not ready yet" : "Title not found"}</h1>
        <p>${known
          ? "This title is still processing, or its stream failed. Refresh in a moment."
          : `No video named “${esc(name)}” was returned by the playback API.`}</p>
        ${note ? `<p class="muted small">${esc(note)}</p>` : ""}
        <button class="btn primary" id="retry-watch">Refresh</button>
        <a class="btn glass" href="#/">Back to catalog</a>
      </section>
    `;
    return;
  }

  let watchHistoryId = null;
  let resumeAt = 0;
  if (state.token && video.videoId != null) {
    try {
      const data = await gql(
        `mutation($input: WatchHistoryCreateInput) {
           create_watch_history(watchHistoryInput: $input) { watchHistoryId timestamp }
         }`,
        { input: { videoId: video.videoId } },
      );
      watchHistoryId = data.create_watch_history?.watchHistoryId || null;
      resumeAt = Number(data.create_watch_history?.timestamp) || 0;
    } catch (error) {
      note = note || `Could not start watch history: ${error.message}`;
    }
  }

  if (gen !== generation) return;
  renderWatchLayout(video, { watchHistoryId, notice: note, resumeAt });
}

/* ------------------------------------------------------------------ */
/* Watch history                                                      */
/* ------------------------------------------------------------------ */

async function renderHistory(gen) {
  if (!state.token) {
    viewEl.innerHTML = `
      <section class="empty-state">
        <span class="empty-icon">🕘</span>
        <h1>Your watch history</h1>
        <p>Sign in to see the titles you have watched and the progress saved by the player.</p>
        <button class="btn primary" data-open-auth="login">Sign in</button>
      </section>
    `;
    return;
  }

  renderLoading("Loading watch history…");

  try {
    const data = await gql(`query { watchHistory { watchHistoryId userId videoId date timestamp } }`);
    if (gen !== generation) return;

    const entries = (data.watchHistory || []).filter(Boolean);

    const uniqueIds = [...new Set(entries.map((entry) => entry.videoId))];
    const lookups = await Promise.all(uniqueIds.map(async (id) => {
      try {
        const result = await gql(
          `query($id: Long!) {
             video_byId(videoId: $id) { videoId name videoLength categorySet { name } }
           }`,
          { id },
        );
        return [id, result.video_byId];
      } catch (error) {
        return [id, null];
      }
    }));

    if (gen !== generation) return;

    const byId = new Map(lookups);

    if (entries.length === 0) {
      viewEl.innerHTML = `
        <section class="empty-state">
          <span class="empty-icon">🍿</span>
          <h2>Nothing here yet</h2>
          <p>Open a title from the catalog and press play — then refresh this page.</p>
          <a class="btn primary" href="#/">Browse titles</a>
        </section>
      `;
      return;
    }

    viewEl.innerHTML = `
      <div class="page-head">
        <div>
          <p class="eyebrow">Your library</p>
          <h1>Watch history</h1>
          <p class="muted">Playback progress is stored server-side through GraphQL.</p>
        </div>
        <button class="btn ghost small" id="refresh-history">Refresh</button>
      </div>
      <div class="history-list">
        ${entries.map((entry) => {
          const video = byId.get(entry.videoId);
          const title = video?.name || `Video #${entry.videoId}`;
          const categories = video ? categoryNames(video) : [];
          const progress = Number(entry.timestamp) || 0;
          const progressLabel = progress > 0 ? ` · stopped at ${formatClock(progress)}` : "";
          const inner = `
            <span class="poster thumb" style="${posterVars(title)}">
              <span class="poster-initials">${esc(initials(title))}</span>
            </span>
            <span class="history-info">
              <h3>${esc(title)}</h3>
              <p class="muted">Watched ${esc(formatDate(entry.date))}${esc(progressLabel)}</p>
              <div class="chips">
                ${categories.slice(0, 2).map((category) => `<span class="chip">${esc(category)}</span>`).join("")}
              </div>
            </span>
            ${video ? `<span class="btn ghost small">${progress > 0 ? "Resume" : "Continue"}</span>` : '<span class="pill subtle">Unavailable</span>'}
          `;
          return video
            ? `<a class="history-row" href="#/watch/${encodeURIComponent(video.name)}">${inner}</a>`
            : `<div class="history-row is-disabled">${inner}</div>`;
        }).join("")}
      </div>
    `;
  } catch (error) {
    if (gen !== generation) return;
    viewEl.innerHTML = errorState("Watch history unavailable", error.message, "retry-history");
  }
}

/* ------------------------------------------------------------------ */
/* Routing                                                            */
/* ------------------------------------------------------------------ */

async function renderRoute() {
  const gen = ++generation;
  destroyPlayer();

  const hash = location.hash.replace(/^#/, "") || "/";
  const parts = hash.split("/").filter(Boolean);

  if (parts[0] === "history") {
    setActiveNav("history");
    await renderHistory(gen);
  } else if (parts[0] === "upload") {
    setActiveNav("upload");
    await renderUpload(gen);
  } else if (parts[0] === "watch" && parts[1]) {
    setActiveNav("");
    await renderWatch(decodeURIComponent(parts.slice(1).join("/")), gen);
  } else {
    setActiveNav("browse");
    await renderBrowse(gen);
  }

  if (gen !== generation) return;
  window.scrollTo(0, 0);

  if (state.focusSearch) {
    state.focusSearch = false;
    const search = document.getElementById("search");
    if (search) {
      search.focus();
      search.setSelectionRange(search.value.length, search.value.length);
    }
  }
}

/* ------------------------------------------------------------------ */
/* Events / boot                                                      */
/* ------------------------------------------------------------------ */

function onViewClick(event) {
  const card = event.target.closest("[data-card-name]");
  if (card) {
    const name = card.dataset.cardName;
    if (name) location.hash = `#/watch/${encodeURIComponent(name)}`;
    return;
  }

  if (event.target.closest("#retry-catalog")) {
    state.catalogLoaded = false;
    state.catalogError = "";
    state.videos = [];
    renderRoute();
    return;
  }

  if (event.target.closest("#refresh-history") || event.target.closest("#retry-history")) {
    renderRoute();
  }

  if (event.target.closest("#retry-watch")) {
    renderRoute();
  }
}

function onViewKeydown(event) {
  if (event.key !== "Enter" && event.key !== " ") return;
  const card = event.target.closest("[data-card-name]");
  if (!card) return;
  event.preventDefault();
  card.click();
}

function init() {
  renderHeader();

  viewEl.addEventListener("click", onViewClick);
  viewEl.addEventListener("keydown", onViewKeydown);

  document.addEventListener("click", (event) => {
    const authButton = event.target.closest("[data-open-auth]");
    if (authButton) {
      event.preventDefault();
      openAuth(authButton.dataset.openAuth || "login");
      return;
    }
    if (event.target.closest("[data-close-modal]")) {
      closeModal();
    }
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") closeModal();
  });

  window.addEventListener("hashchange", () => {
    closeModal();
    renderRoute();
  });

  renderRoute();
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", init);
} else {
  init();
}
