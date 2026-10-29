package br.com.shema.crm.tasks.application;

import br.com.shema.crm.tasks.domain.Task;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Traduz o TaskFilter em predicados JPA. */
final class TaskSpecifications {

    private TaskSpecifications() {}

    static Specification<Task> matching(TaskFilter f, OffsetDateTime now) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            switch (f.situation() == null ? TaskFilter.Situation.ABERTAS : f.situation()) {
                case ABERTAS -> p.add(cb.isNull(root.get("completedAt")));
                case ATRASADAS -> {
                    p.add(cb.isNull(root.get("completedAt")));
                    p.add(cb.lessThan(root.get("dueAt"), now));
                }
                case CONCLUIDAS -> p.add(cb.isNotNull(root.get("completedAt")));
                case TODAS -> {}
            }
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + escape(f.q().trim().toLowerCase(Locale.ROOT)) + "%";
                p.add(cb.like(cb.lower(root.get("title")), like, '\\'));
            }
            if (f.ownerId() != null) {
                p.add(cb.equal(root.get("ownerId"), f.ownerId()));
            }
            if (f.dealId() != null) {
                p.add(cb.equal(root.get("dealId"), f.dealId()));
            }
            if (f.tenderId() != null) {
                p.add(cb.equal(root.get("tenderId"), f.tenderId()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
