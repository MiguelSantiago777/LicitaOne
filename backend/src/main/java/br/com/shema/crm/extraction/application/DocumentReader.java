package br.com.shema.crm.extraction.application;

import br.com.shema.crm.extraction.domain.Extraction;
import br.com.shema.crm.extraction.domain.RawReading;

/**
 * Quem lê o PDF. A implementação real chama o Claude; sem chave configurada, a leitura fica desligada; os testes usam
 * uma falsa, sem rede.
 */
public interface DocumentReader {

    record ReadRequest(byte[] pdf, String fileName, Extraction.Kind kind) {}

    /** O que foi lido e quanto custou. {@code model} é o que respondeu (pode ser o de fallback). */
    record Reading(RawReading raw, String model, long inputTokens, long outputTokens) {}

    /**
     * A leitura não deu certo por um motivo que a pessoa entende (documento recusado, grande demais, IA fora do ar).
     * A leitura termina com falha e essa mensagem; não há nova tentativa automática.
     */
    class ReadingFailed extends RuntimeException {

        private final String model;
        private final Long inputTokens;
        private final Long outputTokens;

        public ReadingFailed(String userMessage) {
            this(userMessage, null, null, null, null);
        }

        public ReadingFailed(String userMessage, String model, Long inputTokens, Long outputTokens, Throwable cause) {
            super(userMessage, cause);
            this.model = model;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
        }

        public String model() {
            return model;
        }

        public Long inputTokens() {
            return inputTokens;
        }

        public Long outputTokens() {
            return outputTokens;
        }
    }

    /** Se a leitura está disponível neste ambiente (o botão "Ler com IA" só aparece quando está). */
    boolean enabled();

    Reading read(ReadRequest request);
}
