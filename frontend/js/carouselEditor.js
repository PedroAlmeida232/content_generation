/**
 * carouselEditor.js — Módulo de gerenciamento do formulário de
 * criação de carrossel.
 *
 * Responsabilidades:
 *  1. Carregar os estilos visuais dinamicamente via GET /styles.
 *  2. Extrair e sanitizar os valores de todos os campos do form.
 *  3. Validar os dados segundo as regras de negócio antes do envio.
 */

import { aiApi, ApiError } from "./apiClient.js";

// Regex para validação de UUID v4 no campo context_id.
const UUID_REGEX =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const VALID_ASPECT_RATIOS = ["1:1", "4:5", "9:16"];

export class CarouselEditor {
  /**
   * @param {HTMLFormElement} form - Referência ao elemento <form>.
   */
  constructor(form) {
    this.form = form;
    this._styleSelect = form.querySelector("#style");
    this._formatSelect = form.querySelector("#format");
    this._contextInput = form.querySelector("#context-id");
    this._promptTextarea = form.querySelector("#prompt");
    this._slideCountInput = form.querySelector("#slide-count");
  }

  /**
   * Inicializa o editor: carrega os estilos disponíveis do backend.
   * Deve ser chamado uma única vez ao montar a página.
   *
   * @returns {Promise<void>}
   */
  async init() {
    await this._loadStyles();
  }

  // ── Privado: Carregamento de Estilos ──────────────────────────────

  async _loadStyles() {
    try {
      const styles = await aiApi.get("/styles", { auth: false });

      if (!Array.isArray(styles) || styles.length === 0) {
        this._fallbackStyles();
        return;
      }

      // Limpar opções e repopular dinamicamente
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
        // Capitalizar a primeira letra para exibição
        option.textContent =
          style.charAt(0).toUpperCase() + style.slice(1);
        this._styleSelect.appendChild(option);
      });
    } catch (err) {
      // Em caso de erro de rede, usar fallback estático
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
      option.textContent =
        style.charAt(0).toUpperCase() + style.slice(1);
      this._styleSelect.appendChild(option);
    });
  }

  // ── Extração de Valores ───────────────────────────────────────────

  /**
   * Extrai e sanitiza os valores do formulário.
   *
   * @returns {{ contextId: string, prompt: string, style: string,
   *             aspectRatio: string, slideCount: number }}
   */
  getValues() {
    return {
      contextId: (this._contextInput?.value ?? "").trim(),
      prompt: (this._promptTextarea?.value ?? "").trim(),
      style: this._styleSelect?.value ?? "",
      aspectRatio: this._formatSelect?.value ?? "",
      slideCount: parseInt(
        this._slideCountInput?.value ?? "0",
        10
      ),
    };
  }

  // ── Validação ─────────────────────────────────────────────────────

  /**
   * Valida todos os campos do formulário segundo as regras de negócio.
   *
   * @returns {{ isValid: boolean, values: object, message: string }}
   */
  validate() {
    const values = this.getValues();
    const { contextId, prompt, style, aspectRatio, slideCount } =
      values;

    if (!contextId) {
      return {
        isValid: false,
        values,
        message: "Informe o ID do contexto de marca.",
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
