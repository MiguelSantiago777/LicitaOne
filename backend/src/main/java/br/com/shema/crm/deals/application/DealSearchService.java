package br.com.shema.crm.deals.application;

import br.com.shema.crm.deals.DealSearch;
import br.com.shema.crm.deals.domain.DealStatus;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class DealSearchService implements DealSearch {

    private final DealService deals;

    DealSearchService(DealService deals) {
        this.deals = deals;
    }

    @Override
    public List<Row> search(Query q) {
        Set<DealStatus> statuses = q.statuses() == null || q.statuses().isEmpty()
                ? null
                : Arrays.stream(DealStatus.values())
                        .filter(s -> q.statuses().contains(s.name()))
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(DealStatus.class)));
        PipelineView pipeline = deals.pipeline(new DealFilter(
                q.text(), statuses == null || statuses.isEmpty() ? null : statuses, q.ownerId(), q.agencyId(), null));
        return pipeline.stages().stream()
                .flatMap(s -> s.deals().stream())
                .map(d -> new Row(
                        d.id(),
                        d.title(),
                        d.stageName(),
                        d.status().name(),
                        d.value(),
                        d.owner() == null ? null : d.owner().name(),
                        d.agency() == null ? null : d.agency().name(),
                        d.tender() == null ? null : d.tender().id(),
                        d.openTasks(),
                        d.overdueTask() != null,
                        d.closedAt()))
                .toList();
    }
}
