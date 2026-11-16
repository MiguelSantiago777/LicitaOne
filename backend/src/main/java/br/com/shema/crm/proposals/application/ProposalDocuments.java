package br.com.shema.crm.proposals.application;

import br.com.shema.crm.proposals.domain.Proposal;
import br.com.shema.crm.shared.company.CompanyData;

/** Monta o PDF da proposta, sem assinatura: quem baixa assina no assinador (gov.br ou certificado). */
public interface ProposalDocuments {

    byte[] pdf(Proposal proposal, CompanyData company);

    /**
     * Se o PDF sai no papel timbrado. O timbrado já traz razão social, CNPJ, endereço e contatos, então o cabeçalho em
     * texto não entra e esses dados deixam de faltar.
     */
    default boolean usesLetterhead() {
        return false;
    }
}
