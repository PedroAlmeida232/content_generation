import { logout } from "./auth.js";
import { requireAuthenticatedSession, redirectToLogin } from "./auth-session.js";
import { contextsApi, ApiError } from "./apiClient.js";
import {
  renderBrandContextSkeleton,
  setBusyState,
} from "./loading-states.js";

const FIELD_KEYS = {
  name: "brand_name",
  logo: "logo_url",
  palette: "color_palette",
  tone: "tone",
};

const keyLabels = {
  brand_name: "Nome da marca",
  logo_url: "Logo",
  color_palette: "Paleta de cores",
  tone: "Tom de voz",
};

const form = document.getElementById("brand-context-form");
const feedbackEl = document.getElementById("brand-context-feedback");
const nameInput = document.getElementById("brand-name");
const logoInput = document.getElementById("logo-url");
const paletteInput = document.getElementById("color-palette");
const toneInput = document.getElementById("tone");
const listEl = document.getElementById("brand-context-list");
const previewNameEl = document.getElementById("brand-context-preview-name");
const previewLogoEl = document.getElementById("brand-context-preview-logo");
const previewPaletteEl = document.getElementById("brand-context-preview-palette");
const previewToneEl = document.getElementById("brand-context-preview-tone");
const logoutBtn = document.getElementById("brand-context-logout");
const userEmailEl = document.getElementById("brand-context-user-email");
const saveBtn = document.getElementById("brand-context-save");
const resetBtn = document.getElementById("brand-context-reset");

const contextState = new Map();
let isSaving = false;

function setFormControlsDisabled(disabled) {
  [nameInput, logoInput, paletteInput, toneInput, saveBtn, resetBtn].forEach((input) => {
    if (input) {
      input.disabled = disabled;
    }
  });
}

function showFeedback(message, tone = "success") {
  if (!feedbackEl) return;
  feedbackEl.textContent = message;
  feedbackEl.hidden = !message;
  feedbackEl.classList.toggle("is-success", tone === "success");
  feedbackEl.classList.toggle("is-error", tone === "error");
}

function setLoading(isLoading) {
  if (saveBtn) {
    saveBtn.disabled = isLoading;
    saveBtn.textContent = isLoading ? "Salvando…" : "Salvar contexto";
  }
  if (resetBtn) {
    resetBtn.disabled = isLoading;
  }
}

function normalizePalette(value) {
  return String(value ?? "")
    .split(/[\n,;]/)
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(0, 8)
    .join(", ");
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function normalizeStoredValue(key, value) {
  if (key === FIELD_KEYS.palette) {
    return normalizePalette(value);
  }
  return String(value ?? "").trim();
}

function parsePalette(value) {
  return normalizePalette(value)
    .split(",")
    .map((color) => color.trim())
    .filter(Boolean);
}

function safePreviewColor(color) {
  const trimmed = String(color ?? "").trim();
  return /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.test(trimmed) ? trimmed : null;
}

function renderPreview() {
  const brandName = (nameInput?.value ?? "").trim() || "Sua marca";
  const logoUrl = (logoInput?.value ?? "").trim();
  const palette = parsePalette(paletteInput?.value ?? "");
  const tone = (toneInput?.value ?? "").trim();

  if (previewNameEl) {
    previewNameEl.textContent = brandName;
  }

  if (previewLogoEl) {
    if (logoUrl) {
      previewLogoEl.innerHTML = `<img src="${escapeHtml(logoUrl)}" alt="Logo da marca ${escapeHtml(brandName)}" loading="lazy" />`;
    } else {
      previewLogoEl.innerHTML = "<span>Logo</span>";
    }
  }

  if (previewPaletteEl) {
    if (palette.length === 0) {
      previewPaletteEl.innerHTML = "";
    } else {
      previewPaletteEl.innerHTML = palette
        .map((color) => {
          const safeColor = safePreviewColor(color);
          const background = safeColor || "rgba(20, 33, 61, 0.2)";
          return `<span class="brand-context-color" title="${escapeHtml(color)}" style="background:${background}"></span>`;
        })
        .join("");
    }
  }

  if (previewToneEl) {
    previewToneEl.textContent =
      tone || "Defina um tom para visualizar o estilo de comunicação.";
  }
}

function renderEmptyState() {
  if (!listEl) return;
  listEl.innerHTML = `
    <div class="brand-context-empty">
      Nenhum contexto salvo ainda. Preencha o formulário e salve para criar a sua base de marca.
    </div>
  `;
}

function renderLoadingState() {
  if (!listEl) return;
  const skeleton = renderBrandContextSkeleton();
  setBusyState(listEl, true);
  setFormControlsDisabled(true);
  if (previewNameEl) previewNameEl.innerHTML = skeleton.previewName;
  if (previewLogoEl) previewLogoEl.innerHTML = skeleton.previewLogo;
  if (previewPaletteEl) previewPaletteEl.innerHTML = skeleton.previewPalette;
  if (previewToneEl) previewToneEl.innerHTML = skeleton.previewTone;
  listEl.innerHTML = skeleton.list;
}

function focusFieldForKey(contextKey) {
  const fieldMap = {
    [FIELD_KEYS.name]: nameInput,
    [FIELD_KEYS.logo]: logoInput,
    [FIELD_KEYS.palette]: paletteInput,
    [FIELD_KEYS.tone]: toneInput,
  };

  fieldMap[contextKey]?.focus();
}

function renderContextList() {
  if (!listEl) return;

  const keys = [FIELD_KEYS.name, FIELD_KEYS.logo, FIELD_KEYS.palette, FIELD_KEYS.tone];

  listEl.innerHTML = keys
    .map((contextKey) => {
      const context = contextState.get(contextKey);
      const label = keyLabels[contextKey] || contextKey;
      const value = context?.contextValue
        ? (contextKey === FIELD_KEYS.palette
          ? parsePalette(context.contextValue).join(", ")
          : context.contextValue)
        : "Ainda não configurado";

      return `
        <article class="brand-context-item" data-context-id="${context?.id || ""}" data-context-key="${escapeHtml(contextKey)}">
          <div class="brand-context-item-top">
            <h3 class="brand-context-item-key">${escapeHtml(label)}</h3>
            <div class="brand-context-item-actions">
              <button type="button" data-edit-context-key="${escapeHtml(contextKey)}">Preencher</button>
              ${
                context?.id
                  ? `<button type="button" data-delete-context-id="${context.id}" data-delete-context-key="${escapeHtml(contextKey)}">Remover</button>`
                  : ""
              }
            </div>
          </div>
          <p class="brand-context-item-value">${escapeHtml(value)}</p>
        </article>
      `;
    })
    .join("");

  listEl.querySelectorAll("[data-edit-context-key]").forEach((button) => {
    button.addEventListener("click", () => {
      const contextKey = button.dataset.editContextKey;
      const context = contextState.get(contextKey);
      if (context) {
        loadContextIntoForm(context);
      } else {
        focusFieldForKey(contextKey);
      }
    });
  });

  listEl.querySelectorAll("[data-delete-context-id]").forEach((button) => {
    button.addEventListener("click", async () => {
      const id = button.dataset.deleteContextId;
      const contextKey = button.dataset.deleteContextKey;
      const confirmed = window.confirm(
        `Remover o contexto "${keyLabels[contextKey] || contextKey}"?`
      );
      if (confirmed) {
        await deleteContext(id, contextKey);
      }
    });
  });
}

function loadContextIntoForm(context) {
  if (!context) return;
  if (context.contextKey === FIELD_KEYS.name && nameInput) {
    nameInput.value = context.contextValue || "";
  }
  if (context.contextKey === FIELD_KEYS.logo && logoInput) {
    logoInput.value = context.contextValue || "";
  }
  if (context.contextKey === FIELD_KEYS.palette && paletteInput) {
    paletteInput.value = context.contextValue || "";
  }
  if (context.contextKey === FIELD_KEYS.tone && toneInput) {
    toneInput.value = context.contextValue || "";
  }
  renderPreview();
  showFeedback(`Contexto "${keyLabels[context.contextKey] || context.contextKey}" carregado para edição.`, "success");
}

async function deleteContext(contextId, contextKey) {
  try {
    await contextsApi.delete(contextId);
    if (contextKey) {
      contextState.delete(contextKey);
    }
    await loadContexts();
    showFeedback("Contexto removido com sucesso.", "success");
  } catch (err) {
    const message = err instanceof ApiError ? err.message : "Não foi possível remover o contexto.";
    showFeedback(message, "error");
  }
}

async function loadContexts() {
  renderLoadingState();

  try {
    const contexts = await contextsApi.list();
    contextState.clear();

    if (Array.isArray(contexts)) {
      contexts.forEach((context) => {
        contextState.set(context.contextKey, context);
      });
    }

    renderContextList();

    const nameContext = contextState.get(FIELD_KEYS.name);
    const logoContext = contextState.get(FIELD_KEYS.logo);
    const paletteContext = contextState.get(FIELD_KEYS.palette);
    const toneContext = contextState.get(FIELD_KEYS.tone);

    if (nameInput) nameInput.value = nameContext?.contextValue || "";
    if (logoInput) logoInput.value = logoContext?.contextValue || "";
    if (paletteInput) paletteInput.value = paletteContext?.contextValue || "";
    if (toneInput) toneInput.value = toneContext?.contextValue || "";

    renderPreview();
    setBusyState(listEl, false);
    setFormControlsDisabled(false);
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      redirectToLogin("/pages/brand-context.html");
      return;
    }
    setBusyState(listEl, false);
    setFormControlsDisabled(false);
    renderPreview();
    renderEmptyState();
    showFeedback(
      err instanceof ApiError ? err.message : "Não foi possível carregar os contextos de marca.",
      "error"
    );
  }
}

async function upsertContext(contextKey, rawValue) {
  const normalizedValue = normalizeStoredValue(contextKey, rawValue);
  const existing = contextState.get(contextKey);

  if (!normalizedValue) {
    if (existing?.id) {
      await contextsApi.delete(existing.id);
      contextState.delete(contextKey);
    }
    return;
  }

  if (existing?.id) {
    await contextsApi.update(existing.id, contextKey, normalizedValue);
  } else {
    await contextsApi.create(contextKey, normalizedValue);
  }
}

async function handleSubmit(event) {
  event.preventDefault();

  if (isSaving) return;
  isSaving = true;
  setLoading(true);
  showFeedback("");

  try {
    await upsertContext(FIELD_KEYS.name, nameInput?.value);
    await upsertContext(FIELD_KEYS.logo, logoInput?.value);
    await upsertContext(FIELD_KEYS.palette, paletteInput?.value);
    await upsertContext(FIELD_KEYS.tone, toneInput?.value);
    await loadContexts();
    showFeedback("Contexto de marca salvo com sucesso.", "success");
  } catch (err) {
    const message = err instanceof ApiError ? err.message : "Não foi possível salvar o contexto.";
    showFeedback(message, "error");
  } finally {
    isSaving = false;
    setLoading(false);
    setFormControlsDisabled(false);
  }
}

function handleLogout() {
  logout();
  redirectToLogin("/pages/brand-context.html");
}

function handleReset() {
  loadContexts();
}

const session = requireAuthenticatedSession("/pages/brand-context.html");

if (session) {
  if (session.email && userEmailEl) {
    userEmailEl.textContent = session.email;
  }

  form?.addEventListener("submit", handleSubmit);
  logoutBtn?.addEventListener("click", handleLogout);
  resetBtn?.addEventListener("click", handleReset);

  [nameInput, logoInput, paletteInput, toneInput].forEach((input) => {
    input?.addEventListener("input", renderPreview);
  });

  loadContexts();
}
