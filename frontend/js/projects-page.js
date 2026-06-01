/**
 * projects-page.js — Lógica da página Meus Projetos.
 *
 * Funcionalidades:
 *  1. Valida sessão JWT e redireciona para login se necessário.
 *  2. Carrega projetos via GET /projects com paginação, filtro por status e busca textual.
 *  3. Renderiza cards com thumbnail do primeiro slide, título, status e data.
 *  4. Permite excluir projetos com confirmação via modal.
 *  5. Navega para a página de detalhe ao clicar em "Ver detalhe".
 *  6. Exibe estado de loading (skeleton), vazio e erro.
 */

import { logout } from "./auth.js";
import {
  requireAuthenticatedSession,
  redirectToLogin,
} from "./auth-session.js";
import { projectsApi, ApiError } from "./apiClient.js";
import { renderProjectListSkeleton, setBusyState } from "./loading-states.js";

const PAGE_SIZE = 12;

let currentPage = 0;
let currentFilter = "";
let currentSearch = "";
let totalPages = 0;
let pendingDeleteId = null;
let searchDebounceTimer = null;

const grid = document.getElementById("projects-grid");
const pagination = document.getElementById("projects-pagination");
const paginationInfo = document.getElementById("pagination-info");
const prevBtn = document.getElementById("pagination-prev");
const nextBtn = document.getElementById("pagination-next");
const searchForm = document.getElementById("projects-search-form");
const searchInput = document.getElementById("projects-search-input");
const searchClearBtn = document.getElementById("projects-search-clear");

const deleteOverlay = document.getElementById("delete-modal-overlay");
const deleteCancelBtn = document.getElementById("delete-cancel-btn");
const deleteConfirmBtn = document.getElementById("delete-confirm-btn");

const toast = document.getElementById("projects-toast");

const logoutBtn = document.getElementById("projects-logout");
const userEmailEl = document.getElementById("projects-user-email");

const filterBtns = document.querySelectorAll(".projects-filter-btn");

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

let _toastTimer = null;

function showToast(message, type = "success") {
  if (_toastTimer) clearTimeout(_toastTimer);
  toast.textContent = message;
  toast.className = `projects-toast toast-${type} is-visible`;
  _toastTimer = setTimeout(() => {
    toast.className = "projects-toast";
  }, 3500);
}

function renderSkeletons(count = 6) {
  setBusyState(grid, true);
  grid.innerHTML = renderProjectListSkeleton(count);
}

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

  const viewHref = `/pages/project-detail.html?project_id=${encodeURIComponent(id)}`;

  return `
  <article class="project-card" role="listitem" data-project-id="${id}">
    <div class="project-card-thumb">
      ${thumb}
      <div class="project-card-thumb-overlay" aria-hidden="true">
        <a
          href="${viewHref}"
          class="project-card-action-btn btn-view"
          id="view-project-${id}"
          aria-label="Ver detalhe do projeto ${title}"
        >
          👁 Ver detalhe
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
  const filterLabel = currentFilter ? ` com status "${badgeLabel(currentFilter)}"` : "";
  const searchLabel = currentSearch ? ` para "${currentSearch}"` : "";
  return `
  <div class="projects-empty" role="listitem">
    <p class="projects-empty-icon" aria-hidden="true">🎠</p>
    <h2>Nenhum projeto encontrado${searchLabel}${filterLabel}</h2>
    <p>
      ${
        currentSearch || currentFilter
          ? "Tente ajustar a busca/filtro ou crie um novo carrossel pelo editor."
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

async function loadProjects() {
  renderSkeletons();

  try {
    const data = await projectsApi.list({
      page: currentPage,
      size: PAGE_SIZE,
      status: currentFilter || null,
      search: currentSearch || null,
    });

    const projects = data?.content ?? [];
    setBusyState(grid, false);

    if (projects.length === 0) {
      grid.innerHTML = renderEmptyState();
      pagination.hidden = true;
      return;
    }

    grid.innerHTML = projects.map(renderCard).join("");
    updatePagination(data.page ?? 0, data.totalPages ?? 1);

    grid.querySelectorAll("[data-delete-id]").forEach((btn) => {
      btn.addEventListener("click", (e) => {
        e.stopPropagation();
        openDeleteModal(btn.dataset.deleteId, btn.dataset.deleteTitle);
      });
    });
  } catch (err) {
    setBusyState(grid, false);
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

function applyFilter(status) {
  currentFilter = status;
  currentPage = 0;

  if (searchDebounceTimer) {
    clearTimeout(searchDebounceTimer);
    searchDebounceTimer = null;
  }

  filterBtns.forEach((btn) => {
    btn.classList.toggle("is-active", btn.dataset.status === status);
  });

  loadProjects();
}

function normalizeSearch(value) {
  return value.trim();
}

function applySearch(value) {
  if (searchDebounceTimer) {
    clearTimeout(searchDebounceTimer);
    searchDebounceTimer = null;
  }

  currentSearch = normalizeSearch(value);
  currentPage = 0;
  loadProjects();
}

function scheduleSearch(value) {
  if (searchDebounceTimer) {
    clearTimeout(searchDebounceTimer);
  }

  searchDebounceTimer = setTimeout(() => {
    searchDebounceTimer = null;
    applySearch(value);
  }, 300);
}

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
    setTimeout(loadProjects, 500);
  } catch {
    showToast("Erro ao excluir projeto. Tente novamente.", "error");
    setTimeout(loadProjects, 600);
  }
}

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

function handleLogout() {
  logout();
  redirectToLogin("/pages/projects.html");
}

const session = requireAuthenticatedSession("/pages/projects.html");

if (session) {
  if (session.email && userEmailEl) {
    userEmailEl.textContent = session.email;
  }

  if (searchInput) {
    searchInput.value = currentSearch;
  }

  logoutBtn?.addEventListener("click", handleLogout);

  filterBtns.forEach((btn) => {
    btn.addEventListener("click", () => applyFilter(btn.dataset.status));
  });

  prevBtn?.addEventListener("click", handlePrev);
  nextBtn?.addEventListener("click", handleNext);

  searchForm?.addEventListener("submit", (event) => {
    event.preventDefault();
    applySearch(searchInput?.value ?? "");
  });

  searchInput?.addEventListener("input", (event) => {
    scheduleSearch(event.currentTarget.value);
  });

  searchInput?.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      searchInput.value = "";
      applySearch("");
    }
  });

  searchClearBtn?.addEventListener("click", () => {
    if (searchInput) {
      searchInput.value = "";
      searchInput.focus();
    }
    applySearch("");
  });

  deleteCancelBtn?.addEventListener("click", closeDeleteModal);
  deleteConfirmBtn?.addEventListener("click", handleConfirmDelete);

  deleteOverlay?.addEventListener("click", (e) => {
    if (e.target === deleteOverlay) closeDeleteModal();
  });

  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && deleteOverlay.classList.contains("is-visible")) {
      closeDeleteModal();
    }
  });

  loadProjects();
}
