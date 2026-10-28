package br.com.shema.crm.imports.domain;

import java.util.List;

/** Uma aba da planilha (um CSV vira uma aba só). */
public record SheetData(String name, List<SheetRow> rows) {}
