/**
 * carouselPreview.js — Módulo de gerenciamento do preview interativo
 * dos slides gerados.
 *
 * Responsabilidades:
 *  1. Renderizar os slides e indicadores de dots dinamicamente.
 *  2. Gerenciar a navegação prev/next/dots e a transição por CSS transform.
 *  3. Exibir legenda e prompt do slide ativo correspondente.
 *  4. Permitir copiar a legenda do slide ativo para a área de transferência.
 */

export class CarouselPreview {
  /**
   * @param {string} containerId - ID do elemento <section> de visualização.
   */
  constructor(containerId) {
    this.container = document.getElementById(containerId);
    this.track = this.container.querySelector("#carousel-track");
    this.wrapper = this.container.querySelector(".carousel-track-wrapper");
    this.prevBtn = this.container.querySelector("#carousel-prev-btn");
    this.nextBtn = this.container.querySelector("#carousel-next-btn");
    this.dotsContainer = this.container.querySelector("#carousel-dots");
    this.captionText = this.container.querySelector("#slide-caption-text");
    this.promptText = this.container.querySelector("#slide-prompt-text");
    this.slideNumSpan = this.container.querySelector("#current-slide-num");
    this.copyCaptionBtn = this.container.querySelector("#copy-caption-btn");
    this.copyPromptBtn = this.container.querySelector("#copy-prompt-btn");
    this.openImageBtn = this.container.querySelector("#open-image-btn");

    this.slidesData = [];
    this.currentIndex = 0;

    this._initEvents();
  }

  _initEvents() {
    this.prevBtn?.addEventListener("click", () => this.prev());
    this.nextBtn?.addEventListener("click", () => this.next());
    this.copyCaptionBtn?.addEventListener("click", () => this.copyCaption());
    this.copyPromptBtn?.addEventListener("click", () => this.copyPrompt());
  }

  /**
   * Monta os slides no DOM e inicializa a exibição.
   * @param {Array} slides - Array de objetos contendo url, legenda e prompt.
   * @param {string} aspectRatio - Proporção selecionada (ex: "1:1", "4:5", "9:16").
   */
  render(slides, aspectRatio) {
    this.slidesData = slides || [];
    this.currentIndex = 0;

    if (this.wrapper) {
      this.wrapper.className = "carousel-track-wrapper";
      if (aspectRatio) {
        const formattedRatio = aspectRatio.replace(":", "-");
        this.wrapper.classList.add(`ratio-${formattedRatio}`);
      }
    }

    // Limpar elementos antigos
    if (this.track) this.track.innerHTML = "";
    if (this.dotsContainer) this.dotsContainer.innerHTML = "";

    if (this.slidesData.length === 0) {
      if (this.container) this.container.hidden = true;
      return;
    }

    // Criar elementos de slides e dots
    this.slidesData.forEach((slide, index) => {
      // Cria slide card
      const slideDiv = document.createElement("div");
      slideDiv.className = "carousel-slide";
      if (index === 0) slideDiv.classList.add("is-active");

      const img = document.createElement("img");
      img.src = slide.image_url;
      img.alt = `Slide ${slide.slide_order}`;
      img.className = "slide-image";
      img.loading = "lazy";

      const badge = document.createElement("div");
      badge.className = "slide-badge";
      badge.textContent = `Slide ${slide.slide_order}`;

      slideDiv.appendChild(img);
      slideDiv.appendChild(badge);
      this.track?.appendChild(slideDiv);

      // Cria dot correspondente
      const dot = document.createElement("button");
      dot.className = "carousel-dot";
      if (index === 0) dot.classList.add("is-active");
      dot.setAttribute("type", "button");
      dot.setAttribute("aria-label", `Ir para o slide ${slide.slide_order}`);
      dot.addEventListener("click", () => this.goTo(index));
      this.dotsContainer?.appendChild(dot);
    });

    this._updateActiveSlide();
  }

  /**
   * Navega para um slide específico por índice.
   * @param {number} index
   */
  goTo(index) {
    if (index < 0 || index >= this.slidesData.length) return;
    this.currentIndex = index;
    this._updateActiveSlide();
  }

  /**
   * Navega para o slide anterior (com loop).
   */
  prev() {
    if (this.slidesData.length === 0) return;
    const prevIndex =
      (this.currentIndex - 1 + this.slidesData.length) %
      this.slidesData.length;
    this.goTo(prevIndex);
  }

  /**
   * Navega para o próximo slide (com loop).
   */
  next() {
    if (this.slidesData.length === 0) return;
    const nextIndex = (this.currentIndex + 1) % this.slidesData.length;
    this.goTo(nextIndex);
  }

  /**
   * Copia o texto da legenda do slide ativo para o clipboard.
   */
  copyCaption() {
    const activeSlide = this.slidesData[this.currentIndex];
    if (!activeSlide || !activeSlide.caption) return;

    navigator.clipboard
      .writeText(activeSlide.caption)
      .then(() => {
        if (!this.copyCaptionBtn) return;
        const originalText = this.copyCaptionBtn.textContent;
        this.copyCaptionBtn.textContent = "Copiado!";
        this.copyCaptionBtn.classList.add("copied");

        setTimeout(() => {
          this.copyCaptionBtn.textContent = originalText;
          this.copyCaptionBtn.classList.remove("copied");
        }, 2000);
      })
      .catch((err) => {
        console.error("[CarouselPreview] Falha ao copiar legenda:", err);
      });
  }

  /**
   * Copia o prompt visual do slide ativo para o clipboard.
   */
  copyPrompt() {
    const activeSlide = this.slidesData[this.currentIndex];
    if (!activeSlide || !activeSlide.prompt_used) return;

    navigator.clipboard
      .writeText(activeSlide.prompt_used)
      .then(() => {
        if (!this.copyPromptBtn) return;
        const originalText = this.copyPromptBtn.textContent;
        this.copyPromptBtn.textContent = "Copiado!";
        this.copyPromptBtn.classList.add("copied");

        setTimeout(() => {
          this.copyPromptBtn.textContent = originalText;
          this.copyPromptBtn.classList.remove("copied");
        }, 2000);
      })
      .catch((err) => {
        console.error("[CarouselPreview] Falha ao copiar prompt:", err);
      });
  }

  /**
   * Atualiza as classes de atividade do DOM e a translação do track.
   */
  _updateActiveSlide() {
    if (this.slidesData.length === 0) return;

    const slidesElements =
      this.track?.querySelectorAll(".carousel-slide") || [];
    const dots = this.dotsContainer?.querySelectorAll(".carousel-dot") || [];

    // Sincronizar classes ativas
    slidesElements.forEach((el, index) => {
      if (index === this.currentIndex) {
        el.classList.add("is-active");
      } else {
        el.classList.remove("is-active");
      }
    });

    dots.forEach((dot, index) => {
      if (index === this.currentIndex) {
        dot.classList.add("is-active");
      } else {
        dot.classList.remove("is-active");
      }
    });

    // Mover a track horizontalmente
    if (this.track) {
      this.track.style.transform = `translateX(-${this.currentIndex * 100}%)`;
    }

    // Atualizar textos descritivos do slide ativo
    const activeSlide = this.slidesData[this.currentIndex];
    if (activeSlide) {
      if (this.slideNumSpan) {
        this.slideNumSpan.textContent = activeSlide.slide_order;
      }
      if (this.captionText) {
        this.captionText.textContent = activeSlide.caption;
      }
      if (this.promptText) {
        this.promptText.textContent = activeSlide.prompt_used || "";
      }
      if (this.openImageBtn) {
        this.openImageBtn.href = activeSlide.image_url || "#";
      }
    }
  }
}
