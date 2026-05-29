/**
 * carouselEditor.js - Módulo de gerenciamento do formulário de
 * criação de carrossel.
 */

import { aiApi, authApi, ApiError } from "./apiClient.js";
import { getApiKey } from "./storage.js";

const UUID_REGEX =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const VALID_ASPECT_RATIOS = ["1:1", "4:5", "9:16"];

export class CarouselEditor {
  constructor(form) {
    this.form = form;
    this._styleSelect = form.querySelector("#style");
    this._formatSelect = form.querySelector("#format");
    this._contextInput = form.querySelector("#context-id");
    this._promptTextarea = form.querySelector("#prompt");
    this._slideCountInput = form.querySelector("#slide-count");
    this._apiKeyInput = form.querySelector("#openai-key");
    this._apiKeyToggle = form.querySelector("#openai-key-toggle");
    this._contextsReady = false;
    this._contextsPromise = null;
  }

  async init() {
    await Promise.all([this._loadStyles(), this.loadContexts()]);
    this._initApiKeyField();
  }

  isReady() {
    return this._contextsReady;
  }

  async loadContexts() {
    if (!this._contextInput) {
      this._contextsReady = true;
      return;
    }

    if (this._contextsPromise) {
      return this._contextsPromise;
    }

    const previousValue = this._contextInput.value;
    const loadPromise = (async () => {
      try {
        const contexts = await authApi.get("/contexts");

        if (!Array.isArray(contexts) || contexts.length === 0) {
          this._fallbackContexts(previousValue);
          return;
        }

        this._contextInput.innerHTML = "";
        this._appendContextPlaceholder();

        contexts.forEach((ctx) => {
          const option = document.createElement("option");
          option.value = ctx.id;
          option.textContent = ctx.contextKey || ctx.id;
          this._contextInput.appendChild(option);
        });

        this._restoreContextSelection(previousValue);
      } catch (err) {
        console.warn(
          "[CarouselEditor] Falha ao carregar contextos do auth-service:",
          err
        );
        this._fallbackContexts(previousValue);
      } finally {
        this._contextsReady = true;
      }
    })();

    this._contextsPromise = loadPromise;

    try {
      await loadPromise;
    } finally {
      this._contextsPromise = null;
    }
  }

  _appendContextPlaceholder() {
    if (!this._contextInput) return;

    const placeholder = document.createElement("option");
    placeholder.value = "";
    placeholder.disabled = true;
    placeholder.selected = true;
    placeholder.textContent = "Selecione um contexto de marca";
    this._contextInput.appendChild(placeholder);
  }

  _fallbackContexts(previousValue = "") {
    if (!this._contextInput) return;

    this._contextInput.innerHTML = "";
    this._appendContextPlaceholder();

    const option = document.createElement("option");
    option.value = "550e8400-e29b-41d4-a716-446655440000";
    option.textContent = "Contexto Padrão (Demonstração)";
    this._contextInput.appendChild(option);

    this._restoreContextSelection(previousValue);
  }

  _restoreContextSelection(previousValue) {
    if (!this._contextInput) return;

    if (previousValue) {
      const hasPrevious = Array.from(this._contextInput.options).some(
        (option) => option.value === previousValue
      );
      if (hasPrevious) {
        this._contextInput.value = previousValue;
        return;
      }
    }

    const firstRealOption = Array.from(this._contextInput.options).find(
      (option) => option.value
    );
    if (firstRealOption) {
      this._contextInput.value = firstRealOption.value;
    }
  }

  _initApiKeyField() {
    const key = getApiKey();
    if (key && this._apiKeyInput) {
      this._apiKeyInput.value = key;
    }

    if (this._apiKeyToggle && this._apiKeyInput) {
      this._apiKeyToggle.addEventListener("click", () => {
        const isPassword = this._apiKeyInput.type === "password";
        this._apiKeyInput.type = isPassword ? "text" : "password";
        this._apiKeyToggle.textContent = isPassword ? "Ocultar" : "Mostrar";
        this._apiKeyToggle.setAttribute(
          "aria-label",
          isPassword ? "Ocultar chave" : "Mostrar chave"
        );
      });
    }
  }

  async _loadStyles() {
    try {
      const styles = await aiApi.get("/styles", { auth: false });

      if (!Array.isArray(styles) || styles.length === 0) {
        this._fallbackStyles();
        return;
      }

      this._styleSelect.innerHTML = "";

      const placeholder = document.createElement("option");
      placeholder.value = "";
      placeholder.disabled = true;
      placeholder.selected = true;
      placeholder.textContent = "Selecione um estilo";
      this._styleSelect.appendChild(placeholder);

      styles.forEach((style) => {
        const option = document.createElement("option");
        option.value = style;
        option.textContent = style.charAt(0).toUpperCase() + style.slice(1);
        this._styleSelect.appendChild(option);
      });
    } catch (err) {
      console.warn(
        "[CarouselEditor] Falha ao carregar estilos do backend:",
        err instanceof ApiError ? `HTTP ${err.status}` : err.message
      );
      this._fallbackStyles();
    }
  }

  _fallbackStyles() {
    const staticStyles = [
      "minimalista",
      "moderno",
      "corporativo",
      "vibrante",
      "editorial",
    ];

    this._styleSelect.innerHTML = "";

    const placeholder = document.createElement("option");
    placeholder.value = "";
    placeholder.disabled = true;
    placeholder.selected = true;
    placeholder.textContent = "Selecione um estilo";
    this._styleSelect.appendChild(placeholder);

    staticStyles.forEach((style) => {
      const option = document.createElement("option");
      option.value = style;
      option.textContent = style.charAt(0).toUpperCase() + style.slice(1);
      this._styleSelect.appendChild(option);
    });
  }

  getValues() {
    return {
      contextId: (this._contextInput?.value ?? "").trim(),
      prompt: (this._promptTextarea?.value ?? "").trim(),
      style: this._styleSelect?.value ?? "",
      aspectRatio: this._formatSelect?.value ?? "",
      slideCount: parseInt(this._slideCountInput?.value ?? "0", 10),
      openaiApiKey: (this._apiKeyInput?.value ?? "").trim(),
    };
  }

  validate() {
    const values = this.getValues();
    const {
      contextId,
      prompt,
      style,
      aspectRatio,
      slideCount,
      openaiApiKey,
    } = values;

    if (!openaiApiKey) {
      return {
        isValid: false,
        values,
        message: "Informe a Chave API da OpenAI.",
      };
    }

    if (!openaiApiKey.startsWith("sk-") || openaiApiKey.length < 20) {
      return {
        isValid: false,
        values,
        message: "A chave API da OpenAI deve começar com 'sk-' e ser válida.",
      };
    }

    if (!contextId) {
      return {
        isValid: false,
        values,
        message: "Selecione um contexto de marca.",
      };
    }

    if (!UUID_REGEX.test(contextId)) {
      return {
        isValid: false,
        values,
        message:
          "O ID do contexto deve ser um UUID válido" +
          " (ex: 550e8400-e29b-41d4-a716-446655440000).",
      };
    }

    if (!prompt || prompt.length < 10) {
      return {
        isValid: false,
        values,
        message: "O briefing deve ter ao menos 10 caracteres.",
      };
    }

    if (prompt.length > 4000) {
      return {
        isValid: false,
        values,
        message: "O briefing não pode exceder 4000 caracteres.",
      };
    }

    if (!style) {
      return {
        isValid: false,
        values,
        message: "Selecione um estilo visual.",
      };
    }

    if (!aspectRatio || !VALID_ASPECT_RATIOS.includes(aspectRatio)) {
      return {
        isValid: false,
        values,
        message: "Selecione um formato de imagem (1:1, 4:5 ou 9:16).",
      };
    }

    if (isNaN(slideCount) || slideCount < 1 || slideCount > 10) {
      return {
        isValid: false,
        values,
        message: "A quantidade de slides deve ser entre 1 e 10.",
      };
    }

    return { isValid: true, values, message: "" };
  }
}
