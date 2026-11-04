package br.com.shema.crm.extraction.application;

import br.com.shema.crm.extraction.domain.Extraction;
import br.com.shema.crm.extraction.domain.RawOrder;
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
     * Um pedido para ler: um arquivo (PDF ou imagem) ou um texto colado, e os itens do contrato já numerados para o
     * modelo relacionar cada linha.
     *
     * @param content PDF ou imagem; nulo quando o pedido é texto
     * @param contentType {@code application/pdf}, {@code image/png} ou {@code image/jpeg}; nulo quando é texto
     * @param text e-mail ou mensagem colada; nulo quando é arquivo
     * @param contract o contrato e seus itens, em texto, como o modelo vê
     */
    record OrderReadRequest(byte[] content, String contentType, String fileName, String text, String contract) {

        public boolean isText() {
            return content == null;
        }
    }

    record OrderRead(RawOrder raw, String model, long inputTokens, long outputTokens) {}

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

    /** Lê um pedido contra os itens de um contrato. */
    default OrderRead readOrder(OrderReadRequest request) {
        throw new ReadingFailed("Este leitor não lê pedidos.");
    }
}
