export function setBusyState(element, isBusy) {
  if (!element) return;
  element.setAttribute("aria-busy", isBusy ? "true" : "false");
}

export function renderProjectListSkeleton(count = 6) {
  return Array.from({ length: count }, (_, index) => `
    <article class="project-card-skeleton skeleton-card loading-surface" role="listitem" aria-label="Carregando projeto ${index + 1}">
      <div class="skeleton skeleton-image"></div>
      <div class="skeleton-card-body loading-stack">
        <span class="skeleton skeleton-line skeleton-line--lg"></span>
        <span class="skeleton skeleton-line skeleton-line--sm"></span>
        <span class="skeleton skeleton-pill"></span>
      </div>
    </article>
  `).join("");
}

export function renderProjectDetailSkeleton(slideCount = 3) {
  const slideSkeletons = Array.from({ length: slideCount }, (_, index) => `
    <article class="project-slide-card loading-surface">
      <div class="project-slide-media">
        <div class="skeleton skeleton-image"></div>
      </div>
      <div class="project-slide-body loading-stack">
        <span class="skeleton skeleton-line skeleton-line--lg"></span>
        <span class="skeleton skeleton-line skeleton-line--xl skeleton-muted"></span>
        <span class="skeleton skeleton-line skeleton-line--md skeleton-muted"></span>
        <span class="skeleton skeleton-line skeleton-line--xs skeleton-muted"></span>
      </div>
      <div class="project-slide-actions">
        <span class="skeleton skeleton-button"></span>
      </div>
    </article>
  `).join("");

  return {
    heroTitle: '<span class="skeleton skeleton-line skeleton-line--xl"></span>',
    heroDescription:
      '<span class="skeleton skeleton-line skeleton-line--lg"></span>' +
      '<span class="skeleton skeleton-line skeleton-line--md skeleton-muted"></span>',
    heroMeta: `
      <span class="skeleton skeleton-pill"></span>
      <span class="skeleton skeleton-pill"></span>
      <span class="skeleton skeleton-pill"></span>
    `,
    previewLabel: '<span class="skeleton skeleton-line skeleton-line--sm"></span>',
    previewFrame: '<div class="skeleton skeleton-frame"></div>',
    slides: slideSkeletons,
  };
}

export function renderBrandContextSkeleton() {
  return {
    previewName: '<span class="skeleton skeleton-line skeleton-line--lg"></span>',
    previewLogo: '<span class="skeleton skeleton-logo"></span>',
    previewPalette: `
      <span class="skeleton skeleton-pill"></span>
      <span class="skeleton skeleton-pill"></span>
      <span class="skeleton skeleton-pill"></span>
    `,
    previewTone:
      '<span class="skeleton skeleton-line skeleton-line--xl"></span>' +
      '<span class="skeleton skeleton-line skeleton-line--md skeleton-muted"></span>',
    list: Array.from({ length: 4 }, (_, index) => `
      <article class="brand-context-item loading-surface skeleton-list-item" aria-label="Carregando contexto ${index + 1}">
        <div class="brand-context-item-top">
          <div class="loading-stack" style="flex: 1;">
            <span class="skeleton skeleton-line skeleton-line--sm"></span>
            <span class="skeleton skeleton-line skeleton-line--md skeleton-muted"></span>
          </div>
          <div class="skeleton-row">
            <span class="skeleton skeleton-pill"></span>
            <span class="skeleton skeleton-pill"></span>
          </div>
        </div>
      </article>
    `).join(""),
  };
}
