import { ApiError } from "./apiClient.js";

function normalizeText(value) {
  return String(value ?? "").trim().toLowerCase();
}

function includesAny(text, patterns) {
  return patterns.some((pattern) => text.includes(pattern));
}

function getBodyText(error) {
  if (!(error instanceof ApiError)) {
    return normalizeText(error?.message);
  }

  const body = error.body;
  if (!body || typeof body !== "object") {
    return normalizeText(error.message);
  }

  return normalizeText(
    body.message ||
      body.detail ||
      body.error ||
      body.error_message ||
      body.errorMessage ||
      error.message
  );
}

function buildRetryHint(kind) {
  switch (kind) {
    case "invalid-api-key":
      return "Abra o campo da chave, corrija o valor e tente novamente.";
    case "credits-exhausted":
      return "A geração foi bloqueada pelo limite atual. Tente novamente mais tarde.";
    case "content-blocked":
      return "Revise o briefing e remova trechos sensíveis, pessoais ou proibidos.";
    case "service-unavailable":
      return "O serviço ficou indisponível. Aguarde alguns instantes e tente novamente.";
    default:
      return "Tente novamente em instantes ou revise os dados enviados.";
  }
}

function buildActionLabel(kind) {
  switch (kind) {
    case "invalid-api-key":
      return "Revisar chave";
    case "credits-exhausted":
      return "Fechar";
    case "content-blocked":
      return "Revisar briefing";
    case "service-unavailable":
      return "Tentar novamente";
    default:
      return "Tentar novamente";
  }
}

function buildActionTarget(kind) {
  switch (kind) {
    case "invalid-api-key":
      return "openai-key";
    case "content-blocked":
      return "prompt";
    default:
      return null;
  }
}

export function classifyGenerationError(error) {
  const messageText = getBodyText(error);
  const status = error instanceof ApiError ? error.status : 0;
  const code = error instanceof ApiError ? normalizeText(error.code) : "";

  const invalidKey =
    status === 401 &&
    includesAny(messageText, [
      "api key",
      "invalid or expired",
      "authorization",
      "unauthorized",
    ]);

  const creditsExhausted =
    status === 429 ||
    code === "rate_limit" ||
    code === "quota_exceeded" ||
    includesAny(messageText, [
      "rate limit",
      "daily generation limit",
      "quota",
      "credits",
      "limite",
    ]);

  const contentBlocked =
    status === 400 &&
    includesAny(messageText, [
      "content filter",
      "content policy",
      "policy violation",
      "rejected",
      "bloquead",
      "blocked",
    ]);

  if (invalidKey) {
    return {
      kind: "invalid-api-key",
      title: "Chave API inválida",
      message:
        "A geração não conseguiu autenticar sua chave da OpenAI. Verifique se ela está correta e ativa.",
      hint: buildRetryHint("invalid-api-key"),
      actionLabel: buildActionLabel("invalid-api-key"),
      actionTarget: buildActionTarget("invalid-api-key"),
      variant: "warning",
    };
  }

  if (creditsExhausted) {
    return {
      kind: "credits-exhausted",
      title: "Limite de créditos atingido",
      message:
        "Sua conta atingiu o limite disponível para gerar conteúdo agora.",
      hint: buildRetryHint("credits-exhausted"),
      actionLabel: buildActionLabel("credits-exhausted"),
      actionTarget: buildActionTarget("credits-exhausted"),
      variant: "warning",
    };
  }

  if (contentBlocked) {
    return {
      kind: "content-blocked",
      title: "Conteúdo bloqueado",
      message:
        "A solicitação foi bloqueada pela política de segurança da OpenAI.",
      hint: buildRetryHint("content-blocked"),
      actionLabel: buildActionLabel("content-blocked"),
      actionTarget: buildActionTarget("content-blocked"),
      variant: "danger",
    };
  }

  if (status >= 500) {
    return {
      kind: "service-unavailable",
      title: "Serviço temporariamente indisponível",
      message:
        "Não foi possível concluir a geração neste momento. Tente novamente em instantes.",
      hint: buildRetryHint("service-unavailable"),
      actionLabel: buildActionLabel("service-unavailable"),
      actionTarget: buildActionTarget("service-unavailable"),
      variant: "error",
    };
  }

  if (status === 404) {
    return {
      kind: "not-found",
      title: "Tarefa não encontrada",
      message:
        "Não encontramos a tarefa consultada. Inicie uma nova geração e tente novamente.",
      hint: "Se o problema persistir, atualize a página e reinicie o fluxo.",
      actionLabel: "Fechar",
      actionTarget: null,
      variant: "warning",
    };
  }

  return {
    kind: "generic",
    title: "Falha na geração",
    message:
      error instanceof ApiError && error.message
        ? error.message
        : "Ocorreu um erro durante a geração.",
    hint: buildRetryHint("generic"),
    actionLabel: buildActionLabel("generic"),
    actionTarget: null,
    variant: "error",
  };
}

