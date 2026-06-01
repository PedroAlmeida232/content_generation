import {
  requireAuthenticatedSession,
  redirectToLogin,
} from "./auth-session.js";
import { projectsApi, ApiError } from "./apiClient.js";

const titleEl = document.getElementById("project-title");
const descriptionEl = document.getElementById("project-description");
const metaEl = document.getElementById("project-meta");
const statusEl = document.getElementById("project-status");
const dateEl = document.getElementById("project-date");
const slidesCountEl = document.getElementById("project-slides-count");
const slidesSummaryEl = document.getElementById("project-slides-summary");
const slidesGridEl = document.getElementById("project-slides-grid");
const previewFrameEl = document.getElementById("project-preview-frame");
const openEditorBtn = document.getElementById("open-editor-btn");
const downloadZipBtn = document.getElementById("download-zip-btn");
const toastEl = document.getElementById("project-detail-toast");

let toastTimer = null;
let currentProject = null;
let isDownloadingZip = false;
const downloadingSlideIds = new Set();

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function slugify(value) {
  return String(value ?? "project")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "") || "project";
}

function getFilenameFromHeaders(headers, fallbackFilename) {
  const contentDisposition = headers?.get("content-disposition") || "";
  const filenameStarMatch = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (filenameStarMatch?.[1]) {
    try {
      return decodeURIComponent(filenameStarMatch[1]);
    } catch {
      return filenameStarMatch[1];
    }
  }

  const filenameMatch = contentDisposition.match(/filename="?([^";]+)"?/i);
  if (filenameMatch?.[1]) {
    return filenameMatch[1];
  }

  return fallbackFilename;
}

function triggerDownload(blob, filename) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function setZipButtonState(state = "idle") {
  if (!downloadZipBtn) return;
  if (state === "loading") {
    downloadZipBtn.disabled = true;
    downloadZipBtn.textContent = "Baixando ZIP…";
    return;
  }

  downloadZipBtn.disabled = state === "disabled";
  downloadZipBtn.textContent = "Baixar ZIP";
}

function setSlideDownloadState(slideId, isLoading) {
  const button = document.querySelector(`[data-download-slide-id="${slideId}"]`);
  if (!button) return;
  button.disabled = isLoading;
  button.textContent = isLoading ? "Baixando…" : "Baixar slide";
}

async function handleDownloadSlide(slideId) {
  const projectId = getProjectId();
  if (!projectId || !slideId || downloadingSlideIds.has(slideId)) return;

  downloadingSlideIds.add(slideId);
  setSlideDownloadState(slideId, true);

  try {
    const result = await projectsApi.downloadSlide(projectId, slideId);
    const projectBase = slugify(currentProject?.title || "project");
    const fallbackName = `${projectBase}-slide-${slideId}.bin`;
    const filename = getFilenameFromHeaders(result.headers, fallbackName);
    triggerDownload(result.blob, filename);
    showToast("Download do slide iniciado.");
  } catch (err) {
    const message = err instanceof ApiError ? err.message : "Não foi possível baixar o slide.";
    showToast(message);
  } finally {
    downloadingSlideIds.delete(slideId);
    setSlideDownloadState(slideId, false);
  }
}

async function handleDownloadZip() {
  const projectId = getProjectId();
  if (!projectId || isDownloadingZip) return;

  isDownloadingZip = true;
  setZipButtonState("loading");

  try {
    const result = await projectsApi.downloadZip(projectId);
    const fallbackName = `${slugify(currentProject?.title || "project")}.zip`;
    const filename = getFilenameFromHeaders(result.headers, fallbackName);
    triggerDownload(result.blob, filename);
    showToast("Download do ZIP iniciado.");
  } catch (err) {
    const message = err instanceof ApiError ? err.message : "Não foi possível baixar o ZIP.";
    showToast(message);
  } finally {
    isDownloadingZip = false;
    setZipButtonState(currentProject?.slides?.length ? "idle" : "disabled");
  }
}

function getProjectId() {
  const params = new URLSearchParams(window.location.search);
  return params.get("project_id");
}

function formatDateTime(value) {
  if (!value) return "Data indisponível";
  try {
    return new Intl.DateTimeFormat("pt-BR", {
      dateStyle: "long",
      timeStyle: "short",
    }).format(new Date(value));
  } catch {
    return value;
  }
}

function badgeClass(status) {
  const map = {
    done: "badge-done",
    draft: "badge-draft",
    generating: "badge-generating",
    processing: "badge-processing",
    failed: "badge-failed",
  };
  return map[status?.toLowerCase()] ?? "badge-draft";
}

function badgeLabel(status) {
  const map = {
    done: "Concluído",
    draft: "Rascunho",
    generating: "Gerando",
    processing: "Processando",
    failed: "Falhou",
  };
  return map[status?.toLowerCase()] ?? status ?? "—";
}

function showToast(message) {
  if (!toastEl) return;
  if (toastTimer) clearTimeout(toastTimer);
  toastEl.textContent = message;
  toastEl.classList.add("is-visible");
  toastTimer = setTimeout(() => {
    toastEl.classList.remove("is-visible");
  }, 2800);
}

function renderLoadingState() {
  if (titleEl) titleEl.textContent = "Carregando projeto…";
  if (descriptionEl) descriptionEl.textContent = "Buscando informações do projeto e seus slides.";
  if (metaEl) metaEl.hidden = true;
  setZipButtonState("disabled");
  if (slidesGridEl) {
    slidesGridEl.innerHTML = `
      <div class="project-detail-loading">
        <p class="project-detail-state-title">Carregando slides…</p>
        <p class="project-detail-state-copy">Estamos montando os dados do projeto.</p>
      </div>
    `;
  }
}

function renderErrorState(title, message, retryHandler) {
  if (slidesGridEl) {
    slidesGridEl.innerHTML = `
      <div class="project-detail-error">
        <p class="project-detail-state-title">${escapeHtml(title)}</p>
        <p class="project-detail-state-copy">${escapeHtml(message)}</p>
        <div class="project-detail-error-actions">
          <button id="project-detail-retry" class="project-detail-btn project-detail-btn--primary" type="button">
            Tentar novamente
          </button>
          <a class="project-detail-btn" href="/pages/projects.html">Voltar para a lista</a>
        </div>
      </div>
    `;
    document.getElementById("project-detail-retry")?.addEventListener("click", retryHandler);
  }
}

function renderEmptySlidesState() {
  if (slidesGridEl) {
    slidesGridEl.innerHTML = `
      <div class="project-detail-empty">
        <p class="project-detail-state-title">Este projeto ainda não tem slides salvos</p>
        <p class="project-detail-state-copy">
          Você pode abrir este projeto no editor para revisar o briefing e gerar os slides novamente.
        </p>
        <div class="project-detail-empty-actions">
          <a class="project-detail-btn project-detail-btn--primary" id="project-detail-open-editor-empty" href="/pages/editor.html">
            Abrir no editor
          </a>
          <a class="project-detail-btn" href="/pages/projects.html">Voltar para a lista</a>
        </div>
      </div>
    `;
  }
}

function renderSlideCard(slide) {
  const imageMarkup = slide.imageUrl
    ? `<img src="${escapeHtml(slide.imageUrl)}" alt="Slide ${slide.slideOrder}" loading="lazy" />`
    : `<div class="project-slide-placeholder"><span>Sem imagem disponível</span></div>`;

  return `
    <article class="project-slide-card">
      <div class="project-slide-media">
        ${imageMarkup}
        <span class="project-slide-badge">Slide ${slide.slideOrder}</span>
      </div>
      <div class="project-slide-body">
        <h3 class="project-slide-title">Slide ${slide.slideOrder}</h3>
        <p class="project-slide-caption">${escapeHtml(slide.caption || "Sem legenda")}</p>
        <p class="project-slide-prompt"><strong>Prompt:</strong> ${escapeHtml(slide.promptUsed || "Não informado")}</p>
        <p class="project-slide-meta">${escapeHtml(formatDateTime(slide.generatedAt))}</p>
      </div>
      <div class="project-slide-actions">
        <button
          type="button"
          class="project-detail-btn project-detail-btn--primary"
          data-download-slide-id="${slide.id}"
        >
          Baixar slide
        </button>
      </div>
    </article>
  `;
}

function setPreviewImage(imageUrl, title) {
  if (!previewFrameEl) return;
  if (imageUrl) {
    previewFrameEl.innerHTML = `<img src="${escapeHtml(imageUrl)}" alt="Prévia do primeiro slide de ${escapeHtml(title)}" loading="lazy" />`;
  } else {
    previewFrameEl.innerHTML = "<span>Sem imagem carregada</span>";
  }
}

async function loadProject() {
  const projectId = getProjectId();
  renderLoadingState();
  currentProject = null;

  if (!projectId) {
    if (titleEl) titleEl.textContent = "Projeto não encontrado";
    if (descriptionEl) descriptionEl.textContent = "O identificador do projeto não foi informado na URL.";
    renderErrorState(
      "Não foi possível abrir o projeto.",
      "A URL está incompleta. Volte para a lista e abra o projeto novamente.",
      loadProject
    );
    return;
  }

  if (openEditorBtn) {
    openEditorBtn.href = `/pages/editor.html?project_id=${encodeURIComponent(projectId)}`;
  }

  try {
    const project = await projectsApi.get(projectId);
    currentProject = project;
    const slides = project?.slides ?? [];

    if (titleEl) titleEl.textContent = project?.title || "Projeto sem título";
    if (descriptionEl) {
      descriptionEl.textContent =
        project?.description?.trim()
          ? project.description
          : "Este projeto não possui descrição.";
    }

    if (metaEl) metaEl.hidden = false;
    if (statusEl) {
      statusEl.className = `project-detail-pill project-badge ${badgeClass(project?.status)}`;
      statusEl.textContent = badgeLabel(project?.status);
    }
    if (dateEl) {
      dateEl.textContent = `Criado em ${formatDateTime(project?.createdAt)}`;
    }
    if (slidesCountEl) {
      slidesCountEl.textContent = `${slides.length} slide${slides.length === 1 ? "" : "s"}`;
    }
    if (slidesSummaryEl) {
      slidesSummaryEl.textContent =
        slides.length > 0
          ? `Exibindo ${slides.length} slide${slides.length === 1 ? "" : "s"} em ordem cronológica.`
          : "Nenhum slide foi salvo para este projeto.";
    }

    const firstImage = slides[0]?.imageUrl ?? project?.firstSlideImageUrl ?? null;
    setPreviewImage(firstImage, project?.title || "projeto");
    setZipButtonState(slides.length === 0 ? "disabled" : "idle");

    if (slides.length === 0) {
      renderEmptySlidesState();
      return;
    }

    if (slidesGridEl) {
      slidesGridEl.innerHTML = slides.map(renderSlideCard).join("");
      slidesGridEl.querySelectorAll("[data-download-slide-id]").forEach((button) => {
        button.addEventListener("click", () => handleDownloadSlide(button.dataset.downloadSlideId));
      });
    }
  } catch (err) {
    let title = "Erro ao carregar projeto";
    let message = "Ocorreu um erro inesperado.";

    if (err instanceof ApiError) {
      if (err.status === 401) {
        redirectToLogin(`/pages/project-detail.html?project_id=${encodeURIComponent(projectId)}`);
        return;
      }
      if (err.status === 404) {
        title = "Projeto não encontrado";
        message = "Não encontramos esse projeto na sua conta.";
      } else {
        message = err.message || message;
      }
    }

    if (titleEl) titleEl.textContent = "Não foi possível carregar o projeto";
    if (descriptionEl) descriptionEl.textContent = message;
    if (metaEl) metaEl.hidden = true;
    setZipButtonState("disabled");
    renderErrorState(title, message, loadProject);
  }
}

const session = requireAuthenticatedSession(`/pages/project-detail.html${window.location.search}`);

if (session) {
  if (session.email) {
    const nav = document.querySelector(".project-detail-nav");
    if (nav) {
      const emailTag = document.createElement("span");
      emailTag.className = "project-detail-pill";
      emailTag.textContent = session.email;
      nav.appendChild(emailTag);
    }
  }

  downloadZipBtn?.addEventListener("click", handleDownloadZip);

  loadProject();

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      toastEl?.classList.remove("is-visible");
    }
  });
}
