/*
 * StreamEra demo frontend.
 * Plain browser JS with hash routing. Everything talks to the Spring GraphQL
 * endpoint at /graphql. The JWT is kept in localStorage and sent as a Bearer
 * token, matching JwtAuthFilter on the backend.
 */

const API_URL = "/graphql";
const DEMO_SRC = "/media/demo.mp4";
const LS_TOKEN = "streamera.token";
const LS_USER = "streamera.username";

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
/* Catalog                                                            */
/* ------------------------------------------------------------------ */

async function loadCatalog() {
  try {
    const data = await gql(`query { videos { name videoLength categorySet { categoryId name } } }`);
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
  const searchText = `${video.name || ""} ${categories.join(" ")}`.toLowerCase();
  return `
    <article class="card" role="button" tabindex="0"
             data-card-name="${esc(video.name)}"
             data-search="${esc(searchText)}"
             aria-label="Open ${esc(video.name)}">
      <div class="poster" style="${posterVars(video.name)}">
        <span class="poster-initials">${esc(initials(video.name))}</span>
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
        <p>The catalog is empty. Seed the mock data or add a video through the GraphQL API, then refresh.</p>
        <button class="btn primary" id="retry-catalog">Refresh catalog</button>
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
      <span class="demo-chip">Demo catalog</span>
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

function attachSource(player, source) {
  const isHls = /m3u8/i.test(source);
  let fellBack = false;
  const badge = document.getElementById("player-badge");

  const useFallback = () => {
    if (fellBack) return;
    fellBack = true;
    destroyPlayer();
    if (badge) badge.textContent = "SAMPLE CLIP";
    toast("Stream unavailable — playing the bundled sample clip.", "info");
    player.src = DEMO_SRC;
    player.load();
    const attempt = player.play();
    if (attempt && typeof attempt.catch === "function") attempt.catch(() => {});
  };

  player.addEventListener("error", useFallback);

  if (isHls && window.Hls && window.Hls.isSupported()) {
    try {
      const hls = new window.Hls({ enableWorker: true });
      state.hls = hls;
      hls.loadSource(source);
      hls.attachMedia(player);
      hls.on(window.Hls.Events.ERROR, (_event, data) => {
        if (data && data.fatal) useFallback();
      });
    } catch (error) {
      useFallback();
    }
    return;
  }

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

function setupPlayer(video, watchHistoryId) {
  const player = document.getElementById("player");
  if (!player) return;

  attachSource(player, video.videoUrl || DEMO_SRC);

  const saveButton = document.getElementById("save-progress");
  if (saveButton) {
    saveButton.addEventListener("click", () => saveProgress(watchHistoryId, player.currentTime));
  }

  if (!watchHistoryId) return;

  let lastSaved = -30;
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
  const statusLabel = status === "OK" ? "Ready" : status === "PENDING" ? "Processing" : status || "Unknown";

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
          <span class="player-badge" id="player-badge">${/demo\.mp4$/i.test(video.videoUrl || "") ? "DEMO CLIP" : "STREAM"}</span>
        </div>
        <h1>${esc(video.name)}</h1>
        <div class="meta-row">
          <span class="pill">${formatDuration(video.videoLength)}</span>
          <span class="pill subtle">${formatSize(video.size)}</span>
          <span class="pill subtle">${esc(statusLabel)}</span>
        </div>
        ${video.videoId != null ? "" : `<div class="notice">The backend did not return full playback metadata for this title. Playing the bundled sample clip as a fallback.</div>`}
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

  setupPlayer(video, watchHistoryId);
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
    const fallback = state.videos.find((item) => item.name === name);
    if (!fallback) {
      viewEl.innerHTML = `
        <section class="empty-state">
          <span class="empty-icon">🔎</span>
          <h1>Title not found</h1>
          <p>No video named “${esc(name)}” was returned by the playback API.</p>
          <a class="btn primary" href="#/">Back to catalog</a>
        </section>
      `;
      return;
    }
    note = note || "This title has not finished processing, so the player is using the bundled sample clip.";
    video = {
      ...fallback,
      videoId: null,
      videoUrl: DEMO_SRC,
      videoStatus: "PENDING",
      size: null,
    };
  }

  let watchHistoryId = null;
  if (state.token && video.videoId != null) {
    try {
      const data = await gql(
        `query($id: Long!) { watchHistoryByVideo(videoId: $id) { watchHistoryId } }`,
        { id: video.videoId },
      );
      watchHistoryId = data.watchHistoryByVideo?.watchHistoryId || null;
    } catch (error) {
      watchHistoryId = null;
    }
  }

  if (gen !== generation) return;
  renderWatchLayout(video, { watchHistoryId, notice: note });
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
    const data = await gql(`query { watchHistory { watchHistoryId userId videoId date } }`);
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
          const inner = `
            <span class="poster thumb" style="${posterVars(title)}">
              <span class="poster-initials">${esc(initials(title))}</span>
            </span>
            <span class="history-info">
              <h3>${esc(title)}</h3>
              <p class="muted">Watched ${esc(formatDate(entry.date))}</p>
              <div class="chips">
                ${categories.slice(0, 2).map((category) => `<span class="chip">${esc(category)}</span>`).join("")}
              </div>
            </span>
            ${video ? '<span class="btn ghost small">Continue</span>' : '<span class="pill subtle">Unavailable</span>'}
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

  if (event.target.closest("#refresh-history")) {
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
