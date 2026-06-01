/**
 * editor-page.js — Lógica da página de criação de carrossel.
 *
 * Fluxo:
 *  1. Valida sessão JWT e OpenAI Key no localStorage.
 *  2. Inicializa o CarouselEditor (carrega estilos via API).
 *  3. Ao submeter o formulário, delega validação ao CarouselEditor.
 *  4. Exibe o modal de progresso e dispara POST /generate/carousel.
 *  5. Faz polling em GET /jobs/{job_id} a cada 1.5 segundos.
 *  6. Atualiza a barra de progresso e o texto de status dinamicamente.
 *  7. Em caso de sucesso, exibe o resultado. Em erro, exibe feedback.
 */

import { requireAuthenticatedSession } from "./auth-session.js";
import { aiApi, projectsApi, ApiError } from "./apiClient.js";
import { classifyGenerationError } from "./error-feedback.js";
import { getApiKey, saveApiKey } from "./storage.js";
import { CarouselEditor } from "./carouselEditor.js";
import { CarouselPreview } from "./carouselPreview.js";

// ── Intervalo de polling (ms) ─────────────────────────────────────
const POLL_INTERVAL_MS = 3000;

// ── Mapeamento de faixas de progresso para mensagens de status ────
const STATUS_MESSAGES = [
  { upTo: 30, message: "Gerando a estrutura de texto dos slides…" },
  { upTo: 50, message: "Redigindo os prompts visuais de cada slide…" },
  { upTo: 95, message: "Renderizando imagem do slide via DALL-E 3…" },
  { upTo: 100, message: "Finalizando composição e compilando carrossel…" },
];

// ── Referências do DOM ────────────────────────────────────────────
const form = document.getElementById("editor-form");
const submitBtn = document.getElementById("editor-submit");
const formFeedback = document.getElementById("editor-form-feedback");

const overlay = document.getElementById("progress-overlay");
const progressFill = document.getElementById("progress-bar-fill");
const progressPct = document.getElementById("progress-percentage");
const progressMsg = document.getElementById("progress-status-msg");
const progressBarRegion = document.getElementById("progress-bar-region");

const successBlock = document.getElementById("progress-success");
const errorBlock = document.getElementById("progress-error");
const errorMsg = document.getElementById("progress-error-msg");
const errorHint = document.getElementById("progress-error-hint");
const errorActionBtn = document.getElementById("progress-error-action");
const closeBtn = document.getElementById("progress-close-btn");
const viewLink = document.getElementById("progress-view-link");
const spinnerWrap = document.getElementById("progress-spinner");

// ── Instâncias dos Componentes ─────────────────────────────────────
const editor = new CarouselEditor(form);
const preview = new CarouselPreview("editor-preview");

// ── Estado interno do polling ─────────────────────────────────────
let pollingTimer = null;
let isPolling = false;
let currentSlides = [];
let currentAspectRatio = "1:1";
let editorReady = false;
let currentErrorAction = null;
const originalSubmitHtml = submitBtn.innerHTML;

submitBtn.disabled = true;

// ── Helpers ───────────────────────────────────────────────────────

function setFormControlsDisabled(disabled) {
  const controls = form.querySelectorAll("input, select, textarea, button");
  controls.forEach((control) => {
    control.disabled = disabled;
  });
}

function getStatusMessage(progress) {
  for (const entry of STATUS_MESSAGES) {
    if (progress < entry.upTo) {
      return entry.message;
    }
  }
  return STATUS_MESSAGES[STATUS_MESSAGES.length - 1].message;
}

function setProgress(pct) {
  const clamped = Math.min(100, Math.max(0, Math.round(pct)));
  progressFill.style.width = `${clamped}%`;
  progressPct.textContent = `${clamped}%`;
  progressBarRegion.setAttribute("aria-valuenow", String(clamped));
}

function clearFormError() {
  formFeedback.textContent = "";
  formFeedback.className = "editor-form-feedback";
}

function setFormError(message, variant = "error") {
  formFeedback.textContent = message;
  formFeedback.className = `editor-form-feedback is-${variant}`;
}

function focusEditorField(fieldId) {
  const field = document.getElementById(fieldId);
  if (!field) return;
  field.focus();
  field.scrollIntoView({ behavior: "smooth", block: "center" });
}

// ── Modal de Progresso ────────────────────────────────────────────

function showOverlay() {
  overlay.classList.add("is-visible");
  document.body.style.overflow = "hidden";
}

function hideOverlay() {
  overlay.classList.remove("is-visible");
  document.body.style.overflow = "";
}

function resetModal() {
  setProgress(0);
  progressMsg.textContent = "Iniciando geração…";
  successBlock.classList.remove("is-visible");
  errorBlock.classList.remove("is-visible");
  errorBlock.dataset.errorKind = "";
  if (errorHint) {
    errorHint.hidden = true;
    errorHint.textContent = "";
  }
  if (errorActionBtn) {
    errorActionBtn.textContent = "Tentar novamente";
    errorActionBtn.disabled = false;
  }
  currentErrorAction = null;
  spinnerWrap.removeAttribute("hidden");
}

async function persistGeneratedProject(jobId, slides) {
  try {
    const values = editor.getValues();
    const title = values.prompt.slice(0, 80) || `Carrossel ${new Date().toLocaleDateString("pt-BR")}`;
    const project = await projectsApi.create(title, null, "done");
    const slidePayload = (slides || []).map((s) => ({
      slide_order: s.slide_order,
      image_url: s.image_url,
      caption: s.caption,
      prompt_used: s.prompt_used,
    }));
    if (slidePayload.length > 0) {
      await projectsApi.saveSlides(project.id, slidePayload);
    }
    return project.id;
  } catch (err) {
    console.warn("[editor-page] Falha ao persistir projeto:", err);
    return null;
  }
}

async function showSuccess(jobId, slides) {
  spinnerWrap.setAttribute("hidden", "");
  setProgress(100);
  progressMsg.textContent = "Carrossel gerado com sucesso!";
  currentSlides = slides || [];
  const projectId = await persistGeneratedProject(jobId, slides);
  if (projectId && viewLink) {
    viewLink.href = `/pages/projects.html`;
  }
  successBlock.classList.add("is-visible");
}

function showError(error) {
  const feedback = classifyGenerationError(error);
  spinnerWrap.setAttribute("hidden", "");
  errorBlock.dataset.errorKind = feedback.kind;
  errorMsg.textContent = feedback.message;
  if (errorHint) {
    errorHint.hidden = !feedback.hint;
    errorHint.textContent = feedback.hint || "";
  }
  if (errorActionBtn) {
    errorActionBtn.textContent = feedback.actionLabel;
  }
  currentErrorAction = () => {
    if (feedback.actionTarget) {
      hideOverlay();
      setFormControlsDisabled(false);
      submitBtn.innerHTML = originalSubmitHtml;
      focusEditorField(feedback.actionTarget);
      return;
    }

    if (feedback.kind === "credits-exhausted") {
      hideOverlay();
      setFormControlsDisabled(false);
      submitBtn.innerHTML = originalSubmitHtml;
      return;
    }

    hideOverlay();
    setFormControlsDisabled(false);
    submitBtn.innerHTML = originalSubmitHtml;
    if (feedback.kind === "service-unavailable" || feedback.kind === "generic") {
      return;
    }
  };
  errorBlock.classList.add("is-visible");
}

// ── Loop de Polling ───────────────────────────────────────────────

function stopPolling() {
  if (pollingTimer !== null) {
    clearInterval(pollingTimer);
    pollingTimer = null;
  }
  isPolling = false;
}

async function pollJobStatus(jobId) {
  // Evita requisições concorrentes se o intervalo disparar antes
  // da anterior terminar
  if (isPolling) {
    return;
  }

  isPolling = true;

  try {
    const data = await aiApi.get(`/jobs/${jobId}`);
    const status = data?.status;
    const progress =
      typeof data?.progress === "number" ? data.progress : null;

    if (status === "done") {
      stopPolling();
      await showSuccess(jobId, data?.slides);
      return;
    }

    if (status === "failed") {
      stopPolling();
      showError(
        data?.error
          ? new ApiError(data.error, { status: 502, body: data })
          : new ApiError("O servidor reportou falha na geração do carrossel.", {
              status: 502,
              body: data,
            })
      );
      return;
    }

    // Status "processing" ou "pending": atualizar progresso
    if (progress !== null) {
      setProgress(progress);
      progressMsg.textContent = getStatusMessage(progress);
    }
  } catch (err) {
    if (
      err instanceof ApiError &&
      err.status >= 400 &&
      err.status < 500
    ) {
      // Erro definitivo (ex: 404 job não encontrado)
      stopPolling();
      showError(err);
    }
    // Para erros de rede temporários, continua o polling silenciosamente
  } finally {
    isPolling = false;
  }
}

function startPolling(jobId) {
  pollingTimer = setInterval(() => {
    pollJobStatus(jobId);
  }, POLL_INTERVAL_MS);
}

// ── Submissão do Formulário ───────────────────────────────────────

async function handleFormSubmit(event) {
  event.preventDefault();
  clearFormError();

  if (!editorReady) {
    setFormError("Aguarde o carregamento dos contextos do editor.");
    return;
  }

  // 1. Delegar validação ao CarouselEditor
  const { isValid, values, message } = editor.validate();
  if (!isValid) {
    setFormError(message);
    return;
  }

  const { contextId, prompt, style, aspectRatio, slideCount, openaiApiKey } =
    values;

  // 2. Persistir a OpenAI API Key validada no localStorage
  saveApiKey(openaiApiKey);

  // Salvar a proporção para renderização do preview posterior
  currentAspectRatio = aspectRatio;

  // Ocultar preview anterior
  const previewSection = document.getElementById("editor-preview");
  if (previewSection) {
    previewSection.setAttribute("hidden", "");
  }

  // 3. Mostrar modal e desabilitar formulário
  resetModal();
  showOverlay();
  setFormControlsDisabled(true);
  submitBtn.innerHTML = `<span class="editor-submit-icon" aria-hidden="true">✦</span> Gerando carrossel...`;

  try {
    // 4. Disparar geração
    const result = await aiApi.post(
      "/generate/carousel",
      {
        context_id: contextId,
        prompt,
        style,
        aspect_ratio: aspectRatio,
        slide_count: slideCount,
      },
      { openaiKey: true }
    );

    const jobId = result?.job_id;
    if (!jobId) {
      throw new Error("Resposta inesperada: job_id não encontrado.");
    }

    // 5. Iniciar loop de polling
    startPolling(jobId);
  } catch (err) {
    stopPolling();
    hideOverlay();
    setFormControlsDisabled(false);
    submitBtn.innerHTML = originalSubmitHtml;

    let message = "Erro ao iniciar a geração. Tente novamente.";
    let errorToShow = err;
    if (err instanceof ApiError) {
      if (err.status === 401) {
        message = "Sessão expirada ou chave inválida.";
      } else if (err.status === 400) {
        message = err.message;
      } else if (err.status === 502) {
        message = err.message;
      }
      errorToShow = err;
    } else if (err instanceof Error) {
      message = err.message;
      errorToShow = new ApiError(message, { status: 500 });
    }

    const feedback = classifyGenerationError(errorToShow);
    setFormError(
      feedback.message || message,
      feedback.variant === "warning" ? "warning" : "error"
    );
  }
}

// ── Fechar modal de erro ──────────────────────────────────────────

function handleCloseBtn() {
  stopPolling();
  hideOverlay();
  setFormControlsDisabled(false);
  submitBtn.innerHTML = originalSubmitHtml;
}

function handleErrorAction() {
  if (typeof currentErrorAction === "function") {
    currentErrorAction();
  }
}

// ── Inicialização ─────────────────────────────────────────────────

const session = requireAuthenticatedSession("/pages/editor.html");

if (session) {
  // Inicializar editor (carrega estilos e contextos dinamicamente)
  editor
    .init()
    .catch((err) => {
      console.error("[editor-page] Falha ao inicializar editor:", err);
    })
    .finally(() => {
      editorReady = true;
      submitBtn.disabled = false;
    });

  form.addEventListener("submit", handleFormSubmit);
  closeBtn.addEventListener("click", handleCloseBtn);
  errorActionBtn?.addEventListener("click", handleErrorAction);

  viewLink?.addEventListener("click", (e) => {
    e.preventDefault();
    hideOverlay();

    // Reabilitar formulário para novas criações
    setFormControlsDisabled(false);
    submitBtn.innerHTML = originalSubmitHtml;

    // Renderiza e exibe o preview dos slides com a proporção selecionada
    preview.render(currentSlides, currentAspectRatio);
    const previewSection = document.getElementById("editor-preview");
    if (previewSection) {
      previewSection.removeAttribute("hidden");
      previewSection.scrollIntoView({ behavior: "smooth" });
    }
  });

  // ── Load existing project if ?project_id= is present ─────────
  const urlParams = new URLSearchParams(window.location.search);
  const existingProjectId = urlParams.get("project_id");
  if (existingProjectId) {
    (async () => {
      try {
        const project = await projectsApi.get(existingProjectId);
        const slides = (project.slides || []).map((s) => ({
          slide_order: s.slideOrder,
          image_url: s.imageUrl,
          caption: s.caption,
          prompt_used: s.promptUsed,
        }));
        if (slides.length > 0) {
          // Hide creation form, show preview directly
          const editorCard = document.querySelector(".editor-card");
          if (editorCard) editorCard.setAttribute("hidden", "");

          currentSlides = slides;
          preview.render(slides, "1:1");
          const previewSection = document.getElementById("editor-preview");
          if (previewSection) {
            previewSection.removeAttribute("hidden");
          }
        }
      } catch (err) {
        console.warn("[editor-page] Falha ao carregar projeto existente:", err);
      }
    })();
  }
}
