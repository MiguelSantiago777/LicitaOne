package br.com.shema.crm.tenders.application;

import br.com.shema.crm.tenders.TenderSearch;
import br.com.shema.crm.tenders.domain.Tender;
import br.com.shema.crm.tenders.domain.TenderStatus;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class TenderSearchService implements TenderSearch {

    private final TenderService tenders;

    TenderSearchService(TenderService tenders) {
        this.tenders = tenders;
    }

    @Override
    public Result search(Query q, int limit) {
        Set<TenderStatus> statuses = q.statuses() == null || q.statuses().isEmpty()
                ? null
                : Arrays.stream(TenderStatus.values())
                        .filter(s -> q.statuses().contains(s.name()))
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(TenderStatus.class)));
        TenderFilter filter = new TenderFilter(
                statuses == null || statuses.isEmpty() ? null : statuses,
                q.text(),
                q.agencyId(),
                null,
                q.state(),
                null,
                q.minValue(),
                null,
                q.from(),
                q.to(),
                q.ownerId(),
                null);
        Page<Tender> page = tenders.search(
                filter,
                PageRequest.of(
                        0,
                        Math.clamp(limit, 1, 100),
                        Sort.by(Sort.Direction.DESC, "totalValue").and(Sort.by("id"))));
        return new Result(
                page.getContent().stream()
                        .map(t -> new Row(
                                t.getId(),
                                t.getAgency().getName(),
                                t.reference(),
                                t.getObject(),
                                t.getStatus().name(),
                                t.getStatus().label(),
                                t.getTotalValue().amount(),
                                t.getWonAt(),
                                t.getAgency().getCity(),
                                t.getAgency().getState(),
                                t.getOwnerId()))
                        .toList(),
                page.getTotalElements());
    }
}
