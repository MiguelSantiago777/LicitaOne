package br.com.shema.crm.tenders.application;

import br.com.shema.crm.tenders.domain.Agency;
import br.com.shema.crm.tenders.domain.Tender;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Traduz o TenderFilter em predicados JPA. Também usado no resumo das abas, para os contadores seguirem o filtro. */
final class TenderSpecifications {

    private TenderSpecifications() {}

    static Specification<Tender> matching(TenderFilter f) {
        return (root, query, cb) -> cb.and(predicates(f, root, cb).toArray(Predicate[]::new));
    }

    static List<Predicate> predicates(TenderFilter f, Root<Tender> root, CriteriaBuilder cb) {
        List<Predicate> p = new ArrayList<>();
        Join<Tender, Agency> agency = root.join("agency", JoinType.INNER);
        if (f.statuses() != null && !f.statuses().isEmpty()) {
            p.add(root.get("status").in(f.statuses()));
        }
        if (f.q() != null && !f.q().isBlank()) {
            String like = "%" + escape(f.q().trim().toLowerCase(Locale.ROOT)) + "%";
            p.add(cb.or(
                    likeLower(cb, agency.get("name"), like),
                    likeLower(cb, agency.get("city"), like),
                    likeLower(cb, root.get("object"), like),
                    likeLower(cb, root.get("materialType"), like),
                    cb.like(root.get("uasg"), like, '\\'),
                    cb.like(root.get("processNumber"), like, '\\')));
        }
        if (f.agencyId() != null) {
            p.add(cb.equal(agency.get("id"), f.agencyId()));
        }
        if (f.city() != null && !f.city().isBlank()) {
            p.add(cb.equal(cb.lower(agency.get("city")), f.city().trim().toLowerCase(Locale.ROOT)));
        }
        if (f.state() != null && !f.state().isBlank()) {
            p.add(cb.equal(agency.get("state"), f.state().trim().toUpperCase(Locale.ROOT)));
        }
        if (f.materialType() != null && !f.materialType().isBlank()) {
            p.add(cb.equal(
                    cb.lower(root.get("materialType")), f.materialType().trim().toLowerCase(Locale.ROOT)));
        }
        if (f.minValue() != null) {
            p.add(cb.greaterThanOrEqualTo(root.<java.math.BigDecimal>get("totalValue"), f.minValue()));
        }
        if (f.maxValue() != null) {
            p.add(cb.lessThanOrEqualTo(root.<java.math.BigDecimal>get("totalValue"), f.maxValue()));
        }
        if (f.ownerId() != null) {
            p.add(cb.equal(root.get("ownerId"), f.ownerId()));
        }
        if (f.signedBefore() != null) {
            p.add(cb.lessThanOrEqualTo(root.get("signedAt"), f.signedBefore()));
        }
        if (f.from() != null || f.to() != null) {
            Expression<LocalDate> relevant = cb.coalesce(
                    root.<LocalDate>get("closedAt"),
                    cb.coalesce(root.<LocalDate>get("signedAt"), root.<LocalDate>get("wonAt")));
            if (f.from() != null) {
                p.add(cb.greaterThanOrEqualTo(relevant, f.from()));
            }
            if (f.to() != null) {
                p.add(cb.lessThanOrEqualTo(relevant, f.to()));
            }
        }
        return p;
    }

    private static Predicate likeLower(CriteriaBuilder cb, Expression<String> field, String pattern) {
        return cb.like(cb.lower(field), pattern, '\\');
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
