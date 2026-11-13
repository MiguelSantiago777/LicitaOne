package br.com.shema.crm.proposals.domain;

import java.time.LocalDate;
import java.util.List;

/** O conteúdo editável da proposta, tudo de uma vez: a tela salva sozinha a cada alteração. */
public record ProposalDraft(
        ProposalKind kind,
        String clientName,
        String clientCnpj,
        String clientAddress,
        String clientEmail,
        String clientPhones,
        Integer validityDays,
        String paymentTerms,
        String deliveryTerms,
        LocalDate proposalDate,
        String signerName,
        String signerRole,
        String signerCpf,
        List<ProposalItemDraft> items) {}
