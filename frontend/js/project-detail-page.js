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
const toastEl = document.getElementById("project-detail-toast");

let toastTimer = null;

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
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

    if (slides.length === 0) {
      renderEmptySlidesState();
      return;
    }

    if (slidesGridEl) {
      slidesGridEl.innerHTML = slides.map(renderSlideCard).join("");
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

  loadProject();

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      toastEl?.classList.remove("is-visible");
    }
  });
}
