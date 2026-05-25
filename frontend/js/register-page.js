import { AuthError, login, register } from "./auth.js";
import {
  redirectAfterLogin,
  redirectAuthenticatedUser,
} from "./auth-session.js?v=20260525b";

const form = document.querySelector(".login-form");
const nameInput = document.getElementById("name");
const emailInput = document.getElementById("email");
const passwordInput = document.getElementById("password");
const submitButton = document.querySelector(".login-submit");
const feedbackBox = document.getElementById("register-feedback");

let isSubmitting = false;

function setFeedback(message, tone = "error") {
  if (!feedbackBox) {
    return;
  }

  feedbackBox.textContent = message;
  feedbackBox.hidden = !message;
  feedbackBox.classList.toggle("is-error", tone === "error");
  feedbackBox.classList.toggle("is-success", tone === "success");
}

function setSubmittingState(submitting) {
  isSubmitting = submitting;

  if (submitButton) {
    submitButton.disabled = submitting;
    submitButton.textContent = submitting ? "Criando conta..." : "Criar conta";
  }
}

function formatAuthError(error) {
  if (error instanceof AuthError) {
    const fieldMessages = Object.values(error.fields || {}).filter(Boolean);
    if (fieldMessages.length > 0) {
      return fieldMessages.join(" ");
    }

    return error.message;
  }

  return "Nao foi possivel concluir o cadastro. Tente novamente.";
}

function getRedirectTarget() {
  const params = new URLSearchParams(window.location.search);
  const next = params.get("next");

  if (!next || !next.startsWith("/") || next.startsWith("//")) {
    return "/pages/dashboard.html";
  }

  return next;
}

async function handleSubmit(event) {
  event.preventDefault();
  setFeedback("");

  if (isSubmitting) {
    return;
  }

  if (!form?.checkValidity()) {
    form?.reportValidity();
    return;
  }

  const name = nameInput?.value.trim() || "";
  const email = emailInput?.value.trim() || "";
  const password = passwordInput?.value || "";

  if (!name || !email || !password) {
    setFeedback("Informe nome, e-mail e senha.");
    return;
  }

  setSubmittingState(true);

  try {
    await register({ name, email, password });
    setFeedback("Conta criada com sucesso. Entrando na plataforma...", "success");
    await login({ email, password });
    setSubmittingState(false);
    redirectAfterLogin(getRedirectTarget());
  } catch (error) {
    setFeedback(formatAuthError(error));
  } finally {
    if (isSubmitting) {
      setSubmittingState(false);
    }
  }
}

form?.addEventListener("submit", handleSubmit);

if (redirectAuthenticatedUser(getRedirectTarget())) {
  // Authenticated users should not stay on the register screen.
}
