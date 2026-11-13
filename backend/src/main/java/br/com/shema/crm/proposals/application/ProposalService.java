package br.com.shema.crm.proposals.application;

import br.com.shema.crm.identity.UserDirectory;
import br.com.shema.crm.proposals.domain.Proposal;
import br.com.shema.crm.proposals.domain.ProposalDraft;
import br.com.shema.crm.proposals.domain.ProposalNumber;
import br.com.shema.crm.proposals.infrastructure.ProposalNumbers;
import br.com.shema.crm.proposals.infrastructure.ProposalRepository;
import br.com.shema.crm.shared.company.CompanyProperties;
import br.com.shema.crm.shared.security.CurrentUser;
import br.com.shema.crm.shared.web.ApiException;
import br.com.shema.crm.shared.web.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso das propostas. Administrador e Comercial criam e alteram qualquer proposta (a equipe é pequena e uma
 * pessoa termina a proposta da outra); Leitura vê e baixa o PDF.
 */
@Service
@Transactional
public class ProposalService {

    /** O PDF pronto, com o nome do arquivo ("Proposta-2026-0001.pdf"). */
    public record Document(String fileName, byte[] content) {}

    private final ProposalRepository proposals;
    private final ProposalNumbers numbers;
    private final ProposalDocuments documents;
    private final UserDirectory users;
    private final CompanyProperties company;
    private final Clock clock;

    public ProposalService(
            ProposalRepository proposals,
            ProposalNumbers numbers,
            ProposalDocuments documents,
            UserDirectory users,
            CompanyProperties company,
            Clock clock) {
        this.proposals = proposals;
        this.numbers = numbers;
        this.documents = documents;
        this.users = users;
        this.company = company;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<ProposalSummary> search(ProposalFilter filter, Pageable pageable) {
        return proposals
                .findAll(ProposalSpecifications.matching(filter), pageable)
                .map(ProposalSummary::from);
    }

    @Transactional(readOnly = true)
    public ProposalView get(UUID id) {
        return view(load(id));
    }

    /** Cria a proposta já com número, a data de hoje e quem assina (o usuário, com o cargo e o CPF do cadastro). */
    public ProposalView create() {
        CurrentUser me = requireWriter();
        LocalDate today = LocalDate.now(clock);
        Proposal.Signer signer = users.signer(me.id())
                .map(s -> new Proposal.Signer(s.name(), s.jobTitle(), s.cpf()))
                .orElse(null);
        Proposal proposal = Proposal.open(numbers.next(today.getYear()), today, me.id(), signer);
        return view(proposals.saveAndFlush(proposal));
    }

    public ProposalView update(UUID id, long version, ProposalDraft draft) {
        requireWriter();
        Proposal proposal = loadVersion(id, version);
        proposal.update(draft);
        proposals.flush();
        return view(proposal);
    }

    public ProposalView duplicate(UUID id) {
        CurrentUser me = requireWriter();
        Proposal source = load(id);
        LocalDate today = LocalDate.now(clock);
        ProposalNumber number = numbers.next(today.getYear());
        return view(proposals.saveAndFlush(source.duplicate(number, today, me.id())));
    }

    public ProposalView cancel(UUID id, long version) {
        CurrentUser me = requireWriter();
        Proposal proposal = loadVersion(id, version);
        proposal.cancel(me.id(), clock.instant());
        proposals.flush();
        return view(proposal);
    }

    public ProposalView reopen(UUID id, long version) {
        requireWriter();
        Proposal proposal = loadVersion(id, version);
        proposal.reopen();
        proposals.flush();
        return view(proposal);
    }

    @Transactional(readOnly = true)
    public Document pdf(UUID id) {
        Proposal proposal = load(id);
        proposal.requireReadyForDocument();
        return new Document(proposal.number().fileName(), documents.pdf(proposal, company.data()));
    }

    /** Dados da empresa que faltam no cabeçalho do PDF, para a tela avisar. */
    public List<String> missingCompanyData() {
        return company.data().missing().stream()
                .filter(field -> !field.equals("representante legal") && !field.equals("cargo do representante"))
                .toList();
    }

    private Proposal load(UUID id) {
        return proposals
                .findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Proposta não encontrada."));
    }

    private Proposal loadVersion(UUID id, long version) {
        Proposal proposal = load(id);
        if (proposal.getVersion() == null || proposal.getVersion() != version) {
            throw new ApiException(
                    ErrorCode.VERSION_CONFLICT,
                    "Outra pessoa alterou esta proposta enquanto você editava. Recarregue para ver a versão atual.");
        }
        return proposal;
    }

    private static CurrentUser requireWriter() {
        CurrentUser me = CurrentUser.get();
        if (!canWrite(me)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Seu perfil só pode ver as propostas.");
        }
        return me;
    }

    private static boolean canWrite(CurrentUser me) {
        return me.isAdmin() || me.roles().contains("SALES");
    }

    private static ProposalView view(Proposal proposal) {
        return ProposalView.from(proposal, canWrite(CurrentUser.get()));
    }
}
