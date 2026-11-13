package br.com.shema.crm.proposals.application;

import br.com.shema.crm.proposals.domain.Proposal;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.jpa.domain.Specification;

/** Traduz o ProposalFilter em predicados JPA. */
final class ProposalSpecifications {

    private static final Pattern FULL_NUMBER = Pattern.compile("(\\d{4})\\s*/\\s*(\\d{1,6})");
    private static final Pattern SEQUENCE = Pattern.compile("\\d{1,6}");

    private ProposalSpecifications() {}

    static Specification<Proposal> matching(ProposalFilter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (!f.includeCancelled()) {
                p.add(cb.isNull(root.get("cancelledAt")));
            }
            if (f.kind() != null) {
                p.add(cb.equal(root.get("kind"), f.kind()));
            }
            if (f.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("proposalDate"), f.from()));
            }
            if (f.to() != null) {
                p.add(cb.lessThanOrEqualTo(root.get("proposalDate"), f.to()));
            }
            if (f.q() != null && !f.q().isBlank()) {
                String q = f.q().trim();
                List<Predicate> any = new ArrayList<>();
                Matcher full = FULL_NUMBER.matcher(q);
                if (full.matches()) {
                    any.add(cb.and(
                            cb.equal(root.get("numberYear"), Integer.parseInt(full.group(1))),
                            cb.equal(root.get("numberSeq"), Integer.parseInt(full.group(2)))));
                } else if (SEQUENCE.matcher(q).matches()) {
                    any.add(cb.equal(root.get("numberSeq"), Integer.parseInt(q)));
                }
                String name = "%" + escape(q.toLowerCase(Locale.ROOT)) + "%";
                any.add(cb.like(cb.lower(root.get("clientName")), name, '\\'));
                String cnpj = q.replaceAll("[\\s./\\-]", "").toUpperCase(Locale.ROOT);
                if (cnpj.length() >= 4) {
                    any.add(cb.like(root.get("clientCnpj"), "%" + escape(cnpj) + "%", '\\'));
                }
                p.add(cb.or(any.toArray(Predicate[]::new)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
