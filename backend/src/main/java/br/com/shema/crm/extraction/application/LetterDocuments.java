package br.com.shema.crm.extraction.application;

import br.com.shema.crm.shared.company.CompanyData;

/** Gera o .docx de uma carta do kit de carona (Fase 10), a partir do texto que a pessoa revisou. */
public interface LetterDocuments {

    String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /**
     * @param text texto da carta: linha em branco separa parágrafos; linha que começa com "- " vira item de lista
     * @param letterhead põe o cabeçalho da empresa (razão social, CNPJ, endereço e contato)
     */
    byte[] docx(String text, CompanyData company, boolean letterhead);
}
