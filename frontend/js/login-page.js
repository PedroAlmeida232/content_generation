import { AuthError, login, register } from "./auth.js";

const form = document.querySelector(".login-form");
const emailInput = document.getElementById("email");
const passwordInput = document.getElementById("password");
const nameInput = document.getElementById("name");
const nameField = document.getElementById("name-field");
const submitButton = document.querySelector(".login-submit");
const errorBox = document.getElementById("login-error");
const modeLoginButton = document.getElementById("mode-login");
const modeRegisterButton = document.getElementById("mode-register");
const cardTitle = document.getElementById("login-card-title");
const cardDescription = document.getElementById("login-card-description");

let mode = "login";

function setError(message) {
  if (!errorBox) {
    return;
  }
  errorBox.textContent = message;
  errorBox.hidden = !message;
}

function setMode(nextMode) {
  mode = nextMode;
  const isRegister = mode === "register";

  if (nameField) {
    nameField.hidden = !isRegister;
  }
  if (nameInput) {
    nameInput.required = isRegister;
  }

  if (cardTitle) {
    cardTitle.textContent = isRegister ? "Criar conta" : "Entrar";
  }
  if (cardDescription) {
    cardDescription.textContent = isRegister
      ? "Cadastre-se para acessar o workspace de geração de conteúdo."
      : "Use seu e-mail e senha para acessar o workspace de geração de conteúdo.";
  }
  if (submitButton) {
    submitButton.textContent = isRegister ? "Criar conta" : "Acessar plataforma";
  }

  modeLoginButton?.classList.toggle("is-active", !isRegister);
  modeRegisterButton?.classList.toggle("is-active", isRegister);
  setError("");
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

async function handleSubmit(event) {
  event.preventDefault();
  setError("");

  const email = emailInput?.value.trim() || "";
  const password = passwordInput?.value || "";

  if (!email || !password) {
    setError("Informe e-mail e senha.");
    return;
  }

  if (mode === "register") {
    const name = nameInput?.value.trim() || "";
    if (!name) {
      setError("Informe seu nome.");
      return;
    }
  }

  submitButton.disabled = true;

  try {
    if (mode === "register") {
      const name = nameInput.value.trim();
      await register({ email, password, name });
      await login({ email, password });
    } else {
      await login({ email, password });
    }

    window.location.href = "/";
  } catch (error) {
    setError(formatAuthError(error));
  } finally {
    submitButton.disabled = false;
  }
}

modeLoginButton?.addEventListener("click", () => setMode("login"));
modeRegisterButton?.addEventListener("click", () => setMode("register"));
form?.addEventListener("submit", handleSubmit);

setMode("login");
