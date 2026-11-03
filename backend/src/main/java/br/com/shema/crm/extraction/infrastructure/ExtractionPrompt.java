package br.com.shema.crm.extraction.infrastructure;

import br.com.shema.crm.extraction.domain.Extraction;

/** Instruções para o modelo. Ficam num lugar só para dar para revisar e medir mudanças com o script de avaliação. */
final class ExtractionPrompt {

    private ExtractionPrompt() {}

    static String system(String companyName) {
        return """
                Você lê documentos de licitações públicas brasileiras (Lei 14.133/2021 e anteriores) para o CRM da \
                empresa %s, fornecedora de órgãos públicos. O que você extrai vira uma sugestão que uma pessoa confere \
                antes de salvar, então a precisão importa mais do que preencher tudo.

                Regras:
                - Copie só o que está escrito no documento. Nunca deduza nem complete: se um dado não aparece, use "" \
                no valor e 0 na página.
                - Páginas contam a partir de 1, pela ordem das páginas do PDF (não pelo número impresso no rodapé).
                - Datas no formato AAAA-MM-DD. Se o documento diz só "12 meses a partir da assinatura", deixe o fim \
                da vigência em branco e explique nos avisos.
                - Números só com dígitos e ponto decimal, sem separador de milhar e sem "R$": 1234.56.
                - Atas de registro de preços costumam listar vários fornecedores. Extraia só os itens registrados para \
                %s; se não achar a empresa, extraia os itens do fornecedor principal e avise.
                - "Permite adesão" (carona) é SIM só se o documento permite expressamente a adesão por órgãos não \
                participantes, NAO se proíbe expressamente, e "" se não diz nada. Não presuma pela lei.
                - Itens: um por linha da tabela, com a quantidade registrada ou contratada (não a mínima) e o preço \
                unitário registrado. Mantenha a descrição como está, sem resumir.
                - Avisos: frases curtas em português sobre o que ficou ilegível, ambíguo, cortado ou diferente do \
                esperado (ex.: "A tabela da página 4 está ilegível."). Não repita os dados nos avisos.
                """.formatted(companyName, companyName);
    }

    static String user(Extraction.Kind kind) {
        return switch (kind) {
            case ATA -> "Este documento foi cadastrado como ata de registro de preços. Extraia os dados da ata.";
            case CONTRATO ->
                "Este documento foi cadastrado como contrato. Extraia os dados do contrato; o campo de"
                        + " adesão fica em branco, a menos que o contrato fale disso.";
        };
    }
}
