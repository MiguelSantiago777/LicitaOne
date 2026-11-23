package br.com.shema.crm.catalog.application;

import br.com.shema.crm.catalog.domain.Product;
import br.com.shema.crm.catalog.domain.ProductOffer;
import br.com.shema.crm.catalog.domain.Supplier;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Filtros das listas de fornecedores e de produtos em predicados JPA. */
final class CatalogSpecifications {

    private CatalogSpecifications() {}

    /** q acha pela razão social, nome fantasia, CNPJ (com ou sem pontuação) ou cidade, sem acento. */
    static Specification<Supplier> suppliers(String q, UUID nicheId, String state, boolean includeArchived) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (!includeArchived) {
                p.add(cb.isNull(root.get("archivedAt")));
            }
            if (nicheId != null) {
                p.add(cb.isMember(nicheId, root.<java.util.Collection<UUID>>get("nicheIds")));
            }
            if (state != null && !state.isBlank()) {
                p.add(cb.equal(root.get("state"), state.trim().toUpperCase(Locale.ROOT)));
            }
            if (q != null && !q.isBlank()) {
                p.add(text(cb, root.get("searchText"), q));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    /** q acha pelo nome, marca ou código CATMAT/CATSER, sem acento. */
    static Specification<Product> products(String q, UUID nicheId, UUID supplierId, boolean includeArchived) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (!includeArchived) {
                p.add(cb.isNull(root.get("archivedAt")));
            }
            if (nicheId != null) {
                p.add(cb.equal(root.get("nicheId"), nicheId));
            }
            if (supplierId != null) {
                Subquery<UUID> offered = query.subquery(UUID.class);
                Root<Product> sub = offered.from(Product.class);
                var offer = sub.<Product, ProductOffer>join("offers");
                offered.select(sub.get("id")).where(cb.equal(offer.get("supplierId"), supplierId));
                p.add(root.get("id").in(offered));
            }
            if (q != null && !q.isBlank()) {
                p.add(text(cb, root.get("searchText"), q));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private static Predicate text(CriteriaBuilder cb, Expression<String> column, String q) {
        List<Predicate> any = new ArrayList<>();
        any.add(cb.like(column, CatalogAccess.likeTerm(q), '\\'));
        String compact = q.replaceAll("[\\s./\\-]", "");
        if (compact.length() >= 4 && !compact.equals(q.trim())) {
            any.add(cb.like(column, CatalogAccess.likeTerm(compact), '\\'));
        }
        return cb.or(any.toArray(Predicate[]::new));
    }
}
