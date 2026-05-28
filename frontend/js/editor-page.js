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
import { aiApi, ApiError } from "./apiClient.js";
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
const originalSubmitHtml = submitBtn.innerHTML;

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

function showFormError(message) {
  formFeedback.textContent = message;
  formFeedback.className = "editor-form-feedback is-error";
}

function clearFormError() {
  formFeedback.textContent = "";
  formFeedback.className = "editor-form-feedback";
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
  spinnerWrap.removeAttribute("hidden");
}

function showSuccess(jobId, slides) {
  spinnerWrap.setAttribute("hidden", "");
  setProgress(100);
  progressMsg.textContent = "Carrossel gerado com sucesso!";
  currentSlides = slides || [];
  successBlock.classList.add("is-visible");
}

function showError(message) {
  spinnerWrap.setAttribute("hidden", "");
  errorMsg.textContent =
    message ||
    "Ocorreu um erro durante a geração. Tente novamente.";
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
      showSuccess(jobId, data?.slides);
      return;
    }

    if (status === "failed") {
      stopPolling();
      showError(
        data?.error
          ? `Erro do servidor: ${data.error}`
          : "O servidor reportou falha na geração do carrossel."
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
      showError(`Erro ao consultar status: ${err.message}`);
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

  // 1. Delegar validação ao CarouselEditor
  const { isValid, values, message } = editor.validate();
  if (!isValid) {
    showFormError(message);
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
    if (err instanceof ApiError) {
      if (err.status === 401) {
        message = "Sessão expirada. Faça login novamente.";
      } else if (err.status === 400) {
        message = `Dados inválidos: ${err.message}`;
      } else if (err.status === 502) {
        message =
          "Não foi possível conectar ao auth-service." +
          " Tente mais tarde.";
      } else {
        message = `Erro ${err.status}: ${err.message}`;
      }
    } else if (err instanceof Error) {
      message = err.message;
    }

    showFormError(message);
  }
}

// ── Fechar modal de erro ──────────────────────────────────────────

function handleCloseBtn() {
  stopPolling();
  hideOverlay();
  setFormControlsDisabled(false);
  submitBtn.innerHTML = originalSubmitHtml;
}

// ── Inicialização ─────────────────────────────────────────────────

const session = requireAuthenticatedSession("/pages/editor.html");

if (session) {
  // Inicializar editor (carrega estilos dinamicamente)
  editor.init().catch((err) => {
    console.error("[editor-page] Falha ao inicializar editor:", err);
  });

  form.addEventListener("submit", handleFormSubmit);
  closeBtn.addEventListener("click", handleCloseBtn);

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
}
