import { AuthError, getToken, login, register } from "./auth.js";

const form = document.querySelector(".login-form");
const emailInput = document.getElementById("email");
const passwordInput = document.getElementById("password");
const nameInput = document.getElementById("name");
const nameField = document.getElementById("name-field");
const submitButton = document.querySelector(".login-submit");
const feedbackBox = document.getElementById("login-feedback");
const modeLoginButton = document.getElementById("mode-login");
const modeRegisterButton = document.getElementById("mode-register");
const cardTitle = document.getElementById("login-card-title");
const cardDescription = document.getElementById("login-card-description");

let mode = "login";
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
    submitButton.textContent = submitting
      ? mode === "register"
        ? "Criando conta..."
        : "Entrando..."
      : mode === "register"
        ? "Criar conta"
        : "Acessar plataforma";
  }

  modeLoginButton.disabled = submitting;
  modeRegisterButton.disabled = submitting;
}

function setMode(nextMode) {
  if (isSubmitting) {
    return;
  }

  mode = nextMode;
  const isRegister = mode === "register";

  if (nameField) {
    nameField.hidden = !isRegister;
  }
  if (nameInput) {
    nameInput.required = isRegister;
  }
  if (passwordInput) {
    passwordInput.autocomplete = isRegister ? "new-password" : "current-password";
  }

  if (cardTitle) {
    cardTitle.textContent = isRegister ? "Criar conta" : "Entrar";
  }
  if (cardDescription) {
    cardDescription.textContent = isRegister
      ? "Cadastre-se para acessar o workspace de geração de conteúdo."
      : "Use seu e-mail e senha para acessar o workspace de geração de conteúdo.";
  }
  modeLoginButton?.classList.toggle("is-active", !isRegister);
  modeRegisterButton?.classList.toggle("is-active", isRegister);
  modeLoginButton?.setAttribute("aria-selected", String(!isRegister));
  modeRegisterButton?.setAttribute("aria-selected", String(isRegister));
  setFeedback("");
  setSubmittingState(false);
}

function formatAuthError(error) {
  if (error instanceof AuthError) {
    const fieldMessages = Object.values(error.fields || {}).filter(Boolean);
    if (fieldMessages.length > 0) {
      return fieldMessages.join(" ");
    }
    return error.message;
  }

  return "Não foi possível concluir a operação. Tente novamente.";
}

function getRedirectTarget() {
  const params = new URLSearchParams(window.location.search);
  const next = params.get("next");

  if (!next || !next.startsWith("/") || next.startsWith("//")) {
    return "/";
  }

  return next;
}

async function handleSubmit(event) {
  event.preventDefault();
  setFeedback("");

  if (!form?.checkValidity()) {
    form?.reportValidity();
    return;
  }

  const email = emailInput?.value.trim() || "";
  const password = passwordInput?.value || "";

  if (!email || !password) {
    setFeedback("Informe e-mail e senha.");
    return;
  }

  if (mode === "register") {
    const name = nameInput?.value.trim() || "";
    if (!name) {
      setFeedback("Informe seu nome.");
      return;
    }
  }

  setSubmittingState(true);

  try {
    if (mode === "register") {
      const name = nameInput.value.trim();
      await register({ email, password, name });
      setFeedback("Conta criada com sucesso. Entrando na plataforma...", "success");
      await login({ email, password });
    } else {
      await login({ email, password });
    }

    window.location.href = getRedirectTarget();
  } catch (error) {
    setFeedback(formatAuthError(error));
  } finally {
    setSubmittingState(false);
  }
}

modeLoginButton?.addEventListener("click", () => setMode("login"));
modeRegisterButton?.addEventListener("click", () => setMode("register"));
form?.addEventListener("submit", handleSubmit);

if (getToken()) {
  window.location.replace(getRedirectTarget());
}

setMode("login");
