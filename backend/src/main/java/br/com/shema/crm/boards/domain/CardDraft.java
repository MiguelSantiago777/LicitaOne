package br.com.shema.crm.boards.domain;

import java.time.LocalDate;
import java.util.UUID;

/** Conteúdo editável de um card. */
public record CardDraft(String title, String notes, UUID ownerId, LocalDate dueDate, UUID tenderId) {}
