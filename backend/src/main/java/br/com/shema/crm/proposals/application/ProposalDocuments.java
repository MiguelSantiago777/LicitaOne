package br.com.shema.crm.proposals.application;

import br.com.shema.crm.proposals.domain.Proposal;
import br.com.shema.crm.shared.company.CompanyData;

/** Monta o PDF da proposta, sem assinatura: quem baixa assina no assinador (gov.br ou certificado). */
public interface ProposalDocuments {

    byte[] pdf(Proposal proposal, CompanyData company);
}
