package br.com.shema.crm.catalog.application;

import br.com.shema.crm.catalog.domain.CatalogText;
import br.com.shema.crm.catalog.domain.Niche;
import br.com.shema.crm.catalog.infrastructure.NicheRepository;
import br.com.shema.crm.shared.web.ApiException;
import br.com.shema.crm.shared.web.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nichos: criados, renomeados e arquivados pela equipe. A lista mostra quantos fornecedores e produtos tem cada um. */
@Service
@Transactional
public class NicheService {

    @Schema(name = "NicheResponse")
    public record NicheView(
            UUID id,
            String name,
            String description,
            long supplierCount,
            long productCount,
            Instant archivedAt,
            long version,
            boolean canEdit) {}

    /** Nicho citado num fornecedor ou produto. */
    @Schema(name = "NicheRefResponse")
    public record NicheRef(UUID id, String name, boolean archived) {

        static NicheRef of(Niche n) {
            return new NicheRef(n.getId(), n.getName(), n.isArchived());
        }
    }

    private final NicheRepository niches;
    private final Clock clock;

    public NicheService(NicheRepository niches, Clock clock) {
        this.niches = niches;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<NicheView> list(boolean includeArchived) {
        Map<UUID, Long> suppliers = counts(niches.countActiveSuppliers());
        Map<UUID, Long> products = counts(niches.countActiveProducts());
        boolean canEdit = CatalogAccess.canWrite();
        return niches.findAllByOrderByNameKey().stream()
                .filter(n -> includeArchived || !n.isArchived())
                .map(n -> view(n, suppliers.getOrDefault(n.getId(), 0L), products.getOrDefault(n.getId(), 0L), canEdit))
                .toList();
    }

    public NicheView create(String name, String description) {
        CatalogAccess.requireWriter();
        Niche niche = new Niche(name, description);
        requireUniqueName(niche, null);
        return view(niches.saveAndFlush(niche));
    }

    public NicheView describe(UUID id, long version, String name, String description) {
        CatalogAccess.requireWriter();
        Niche niche = CatalogAccess.requireVersion(load(id), version, "este nicho");
        niche.describe(name, description);
        requireUniqueName(niche, id);
        niches.flush();
        return view(niche);
    }

    public NicheView archive(UUID id, long version) {
        CatalogAccess.requireWriter();
        Niche niche = CatalogAccess.requireVersion(load(id), version, "este nicho");
        niche.archive(Instant.now(clock));
        niches.flush();
        return view(niche);
    }

    public NicheView restore(UUID id, long version) {
        CatalogAccess.requireWriter();
        Niche niche = CatalogAccess.requireVersion(load(id), version, "este nicho");
        niche.restore();
        niches.flush();
        return view(niche);
    }

    /** Todos os nichos, por id, para montar as listas de fornecedores e produtos sem buscar um a um. */
    @Transactional(readOnly = true)
    public Map<UUID, Niche> byId() {
        return niches.findAll().stream().collect(Collectors.toMap(Niche::getId, Function.identity()));
    }

    /**
     * Confere os nichos escolhidos: todos existem, e os que entram agora ({@code current} é o que já estava ligado)
     * não estão arquivados.
     */
    void requireAssignable(Collection<UUID> wanted, Collection<UUID> current) {
        if (wanted.isEmpty()) {
            return;
        }
        Map<UUID, Niche> found =
                niches.findAllById(wanted).stream().collect(Collectors.toMap(Niche::getId, Function.identity()));
        for (UUID id : wanted) {
            Niche niche = found.get(id);
            if (niche == null) {
                throw new ApiException(ErrorCode.NOT_FOUND, "Nicho não encontrado.");
            }
            if (niche.isArchived() && !current.contains(id)) {
                throw new ApiException(
                        ErrorCode.BUSINESS_RULE, "O nicho " + niche.getName() + " está arquivado. Escolha outro.");
            }
        }
    }

    private void requireUniqueName(Niche niche, UUID self) {
        niches.findByNameKey(CatalogText.key(niche.getName()))
                .filter(other -> !other.getId().equals(self))
                .ifPresent(other -> {
                    throw new ApiException(
                            ErrorCode.CONFLICT,
                            other.isArchived()
                                    ? "Já existe um nicho arquivado com esse nome. Desarquive-o em vez de criar outro."
                                    : "Já existe um nicho com esse nome.");
                });
    }

    private Niche load(UUID id) {
        return niches.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Nicho não encontrado."));
    }

    private NicheView view(Niche n) {
        Map<UUID, Long> suppliers = counts(niches.countActiveSuppliers());
        Map<UUID, Long> products = counts(niches.countActiveProducts());
        return view(
                n,
                suppliers.getOrDefault(n.getId(), 0L),
                products.getOrDefault(n.getId(), 0L),
                CatalogAccess.canWrite());
    }

    private static NicheView view(Niche n, long suppliers, long products, boolean canEdit) {
        return new NicheView(
                n.getId(),
                n.getName(),
                n.getDescription(),
                suppliers,
                products,
                n.getArchivedAt(),
                n.getVersion() == null ? 0 : n.getVersion(),
                canEdit);
    }

    private static Map<UUID, Long> counts(List<NicheRepository.NicheCount> rows) {
        return rows.stream()
                .collect(
                        Collectors.toMap(NicheRepository.NicheCount::getNicheId, NicheRepository.NicheCount::getTotal));
    }
}
