package br.com.shema.crm.extraction.domain;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * O que o modelo devolve ao ler a resposta de um órgão (Fase 9). Tudo é texto, com {@code ""} para "não diz"; datas e
 * situação são conferidas em {@link ResponseReading#from}.
 */
public record RawResponse(
        @JsonPropertyDescription(
                "Situação do próximo pedido segundo a resposta: PEDIDO_PREVISTO (diz quando vai pedir),"
                        + " SEM_PREVISAO (não sabe ou ainda não tem data), PEDIDO_A_CAMINHO (a própria mensagem já é"
                        + " o pedido ou diz que o pedido/empenho foi enviado), NAO_VAI_PEDIR, PEDE_DOCUMENTO (pede"
                        + " documento, certidão ou informação à empresa) ou OUTRO")
        String outcome,

        @JsonPropertyDescription(
                "Data prevista do pedido, no formato AAAA-MM-DD, convertendo expressões como \"mês que vem\" a partir"
                        + " da data do contato; \"\" se a resposta não dá data")
        String expectedDate,

        @JsonPropertyDescription("true se a data prevista é vaga (\"depois do carnaval\", \"no segundo semestre\")")
        boolean expectedDateIsVague,

        @JsonPropertyDescription(
                "Resumo de uma linha para o histórico, em português, na terceira pessoa (ex.: \"Pedem em março,"
                        + " depois da liberação do orçamento.\")")
        String summary,

        @JsonPropertyDescription(
                "Próxima ação da empresa, como título curto de tarefa (ex.: \"Cobrar o pedido de março\"), ou \"\" se"
                        + " não há nada a fazer")
        String taskTitle,

        @JsonPropertyDescription("Data para fazer a próxima ação, no formato AAAA-MM-DD, ou \"\"")
        String taskDueDate,

        @JsonPropertyDescription("Avisos curtos em português sobre o que ficou ambíguo")
        List<String> warnings) {}
