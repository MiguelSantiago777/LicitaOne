package br.com.shema.crm.shared.domain;

/**
 * Uma regra de negócio foi violada (ex.: assinar sem data de assinatura). Vira HTTP 422 com o código da regra, que
 * o front usa para decidir a mensagem.
 */
public class DomainRuleViolation extends RuntimeException {

    private final String rule;

    public DomainRuleViolation(String rule, String message) {
        super(message);
        this.rule = rule;
    }

    public String rule() {
        return rule;
    }
}
