package br.com.shema.crm.proposals.infrastructure;

import br.com.shema.crm.proposals.domain.Proposal;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProposalRepository extends JpaRepository<Proposal, UUID>, JpaSpecificationExecutor<Proposal> {}
