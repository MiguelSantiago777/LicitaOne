package br.com.shema.crm.tasks.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Conteúdo editável de uma tarefa. */
public record TaskDraft(String title, String notes, OffsetDateTime dueAt, UUID ownerId, UUID dealId, UUID tenderId) {}
