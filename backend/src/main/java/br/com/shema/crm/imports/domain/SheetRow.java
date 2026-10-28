package br.com.shema.crm.imports.domain;

import java.util.List;

/**
 * Uma linha de planilha como texto.
 *
 * @param number número da linha como o usuário vê na planilha (começa em 1)
 */
public record SheetRow(int number, List<String> cells) {

    public String cell(int index) {
        return index >= 0 && index < cells.size() ? cells.get(index) : null;
    }
}
