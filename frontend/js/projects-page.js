/**
 * projects-page.js — Lógica da página Meus Projetos.
 *
 * Funcionalidades:
 *  1. Valida sessão JWT e redireciona para login se necessário.
 *  2. Carrega projetos via GET /projects com paginação e filtro por status.
 *  3. Renderiza cards com thumbnail do primeiro slide, título, status e data.
 *  4. Permite excluir projetos com confirmação via modal.
 *  5. Navega para o editor com o preview do projeto ao clicar em "Visualizar".
 *  6. Exibe estado de loading (skeleton), vazio e erro.
 */

import { logout } from "./auth.js";
import {
  requireAuthenticatedSession,
  redirectToLogin,
} from "./auth-session.js";
import { projectsApi, ApiError } from "./apiClient.js";

// ── Constantes ───────────────────────────────────────────────
const PAGE_SIZE = 12;

// ── Estado da página ─────────────────────────────────────────
let currentPage = 0;
let currentFilter = "";
let totalPages = 0;
let pendingDeleteId = null;

// ── Referências do DOM ────────────────────────────────────────
const grid = document.getElementById("projects-grid");
const pagination = document.getElementById("projects-pagination");
const paginationInfo = document.getElementById("pagination-info");
const prevBtn = document.getElementById("pagination-prev");
const nextBtn = document.getElementById("pagination-next");

const deleteOverlay = document.getElementById("delete-modal-overlay");
const deleteCancelBtn = document.getElementById("delete-cancel-btn");
const deleteConfirmBtn = document.getElementById("delete-confirm-btn");

const toast = document.getElementById("projects-toast");

const logoutBtn = document.getElementById("projects-logout");
const userEmailEl = document.getElementById("projects-user-email");

// ── Filter buttons ────────────────────────────────────────────
const filterBtns = document.querySelectorAll(".projects-filter-btn");

// ── Utilitários ───────────────────────────────────────────────

function formatDate(isoString) {
  if (!isoString) return "—";
  try {
    return new Intl.DateTimeFormat("pt-BR", {
      day: "2-digit",
      month: "short",
      year: "numeric",
    }).format(new Date(isoString));
  } catch {
    return isoString;
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

// ── Toast ─────────────────────────────────────────────────────

let _toastTimer = null;

function showToast(message, type = "success") {
  if (_toastTimer) clearTimeout(_toastTimer);
  toast.textContent = message;
  toast.className = `projects-toast toast-${type} is-visible`;
  _toastTimer = setTimeout(() => {
    toast.className = "projects-toast";
  }, 3500);
}

// ── Skeleton loading ──────────────────────────────────────────

function renderSkeletons(count = 6) {
  grid.setAttribute("aria-busy", "true");
  grid.innerHTML = Array.from({ length: count })
    .map(
      () => `
    <div class="project-card-skeleton" role="listitem" aria-label="Carregando projeto">
      <div class="skeleton-thumb"></div>
      <div class="skeleton-body">
        <div class="skeleton-line"></div>
        <div class="skeleton-line skeleton-line--short"></div>
        <div class="skeleton-line skeleton-line--badge"></div>
      </div>
    </div>`
    )
    .join("");
}

// ── Card rendering ────────────────────────────────────────────

function renderCard(project) {
  const {
    id,
    title,
    status,
    createdAt,
    firstSlideImageUrl,
  } = project;

  const thumb = firstSlideImageUrl
    ? `<img
          src="${firstSlideImageUrl}"
          alt="Thumbnail do primeiro slide de ${title}"
          loading="lazy"
       />`
    : `<div class="project-card-thumb-placeholder">
         <span aria-hidden="true">🎠</span>
       </div>`;

  const viewHref = `/pages/editor.html?project_id=${encodeURIComponent(id)}`;

  return `
  <article class="project-card" role="listitem" data-project-id="${id}">
    <div class="project-card-thumb">
      ${thumb}
      <div class="project-card-thumb-overlay" aria-hidden="true">
        <a
          href="${viewHref}"
          class="project-card-action-btn btn-view"
          id="view-project-${id}"
          aria-label="Visualizar projeto ${title}"
        >
          👁 Visualizar
        </a>
        <button
          type="button"
          class="project-card-action-btn btn-delete"
          data-delete-id="${id}"
          data-delete-title="${title.replace(/"/g, "&quot;")}"
          aria-label="Excluir projeto ${title}"
        >
          🗑 Excluir
        </button>
      </div>
    </div>
    <div class="project-card-body">
      <h2 class="project-card-title">${title}</h2>
      <div class="project-card-meta">
        <span class="project-card-date">${formatDate(createdAt)}</span>
        <span class="project-badge ${badgeClass(status)}">${badgeLabel(status)}</span>
      </div>
    </div>
  </article>`;
}

function renderEmptyState() {
  const filterLabel = currentFilter
    ? ` com status "${badgeLabel(currentFilter)}"`
    : "";
  return `
  <div class="projects-empty" role="listitem">
    <p class="projects-empty-icon" aria-hidden="true">🎠</p>
    <h2>Nenhum projeto encontrado${filterLabel}</h2>
    <p>
      ${
        currentFilter
          ? "Tente outro filtro ou crie um novo carrossel pelo editor."
          : "Você ainda não gerou nenhum carrossel. Comece agora pelo editor!"
      }
    </p>
    <a class="projects-empty-action" href="/pages/editor.html">
      ✦ Criar primeiro carrossel
    </a>
  </div>`;
}

function renderErrorState(message) {
  return `
  <div class="projects-error" role="listitem">
    <p>⚠ Erro ao carregar projetos</p>
    <p>${message}</p>
    <button class="projects-retry-btn" id="retry-btn" type="button">
      Tentar novamente
    </button>
  </div>`;
}

// ── Pagination ────────────────────────────────────────────────

function updatePagination(page, total) {
  totalPages = total;
  if (totalPages <= 1) {
    pagination.hidden = true;
    return;
  }
  pagination.hidden = false;
  paginationInfo.textContent = `Página ${page + 1} de ${total}`;
  prevBtn.disabled = page === 0;
  nextBtn.disabled = page >= total - 1;
}

// ── Data loading ──────────────────────────────────────────────

async function loadProjects() {
  renderSkeletons();

  try {
    const data = await projectsApi.list({
      page: currentPage,
      size: PAGE_SIZE,
      status: currentFilter || null,
    });

    const projects = data?.content ?? [];
    grid.setAttribute("aria-busy", "false");

    if (projects.length === 0) {
      grid.innerHTML = renderEmptyState();
      pagination.hidden = true;
      return;
    }

    grid.innerHTML = projects.map(renderCard).join("");

    updatePagination(data.page ?? 0, data.totalPages ?? 1);

    // Attach delete listeners
    grid.querySelectorAll("[data-delete-id]").forEach((btn) => {
      btn.addEventListener("click", (e) => {
        e.stopPropagation();
        openDeleteModal(
          btn.dataset.deleteId,
          btn.dataset.deleteTitle
        );
      });
    });
  } catch (err) {
    grid.setAttribute("aria-busy", "false");
    let message = "Ocorreu um erro inesperado.";
    if (err instanceof ApiError) {
      if (err.status === 401) {
        redirectToLogin("/pages/projects.html");
        return;
      }
      message = err.message || message;
    }
    grid.innerHTML = renderErrorState(message);
    pagination.hidden = true;

    document.getElementById("retry-btn")?.addEventListener("click", loadProjects);
  }
}

// ── Filter ────────────────────────────────────────────────────

function applyFilter(status) {
  currentFilter = status;
  currentPage = 0;

  filterBtns.forEach((btn) => {
    btn.classList.toggle("is-active", btn.dataset.status === status);
  });

  loadProjects();
}

// ── Delete modal ──────────────────────────────────────────────

function openDeleteModal(projectId, projectTitle) {
  pendingDeleteId = projectId;
  const desc = document.getElementById("delete-modal-desc");
  if (desc) {
    desc.textContent = `"${projectTitle}" será removido permanentemente. Esta ação não pode ser desfeita.`;
  }
  deleteOverlay.classList.add("is-visible");
  deleteConfirmBtn.focus();
}

function closeDeleteModal() {
  deleteOverlay.classList.remove("is-visible");
  pendingDeleteId = null;
}

async function handleConfirmDelete() {
  if (!pendingDeleteId) return;

  const id = pendingDeleteId;
  closeDeleteModal();

  // Optimistically remove card
  const card = grid.querySelector(`[data-project-id="${id}"]`);
  if (card) {
    card.style.transition = "opacity 0.22s, transform 0.22s";
    card.style.opacity = "0";
    card.style.transform = "scale(0.94)";
    setTimeout(() => card.remove(), 240);
  }

  try {
    await projectsApi.delete(id);
    showToast("Projeto excluído com sucesso.", "success");
    // Reload current page to fix grid gaps / empty state
    setTimeout(loadProjects, 500);
  } catch (err) {
    showToast("Erro ao excluir projeto. Tente novamente.", "error");
    // Re-render to restore removed card
    setTimeout(loadProjects, 600);
  }
}

// ── Pagination handlers ───────────────────────────────────────

function handlePrev() {
  if (currentPage > 0) {
    currentPage -= 1;
    loadProjects();
    window.scrollTo({ top: 0, behavior: "smooth" });
  }
}

function handleNext() {
  if (currentPage < totalPages - 1) {
    currentPage += 1;
    loadProjects();
    window.scrollTo({ top: 0, behavior: "smooth" });
  }
}

// ── Logout ────────────────────────────────────────────────────

function handleLogout() {
  logout();
  redirectToLogin("/pages/projects.html");
}

// ── Init ──────────────────────────────────────────────────────

const session = requireAuthenticatedSession("/pages/projects.html");

if (session) {
  if (session.email && userEmailEl) {
    userEmailEl.textContent = session.email;
  }

  logoutBtn?.addEventListener("click", handleLogout);

  filterBtns.forEach((btn) => {
    btn.addEventListener("click", () => applyFilter(btn.dataset.status));
  });

  prevBtn?.addEventListener("click", handlePrev);
  nextBtn?.addEventListener("click", handleNext);

  deleteCancelBtn?.addEventListener("click", closeDeleteModal);
  deleteConfirmBtn?.addEventListener("click", handleConfirmDelete);

  // Close modal on overlay click
  deleteOverlay?.addEventListener("click", (e) => {
    if (e.target === deleteOverlay) closeDeleteModal();
  });

  // Close modal on Escape
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && deleteOverlay.classList.contains("is-visible")) {
      closeDeleteModal();
    }
  });

  loadProjects();
}
