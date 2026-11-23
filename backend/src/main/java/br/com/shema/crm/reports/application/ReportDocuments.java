package br.com.shema.crm.reports.application;

/** Gera o PDF do relatório, com os mesmos números e gráficos da tela. */
public interface ReportDocuments {

    byte[] pdf(ReportView report);
}
