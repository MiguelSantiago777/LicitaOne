/**
 * Fornecedores e produtos por nicho (Fase 13, ADR 0023): nichos cadastrados pela equipe, fornecedores com contatos e
 * os nichos que atendem, produtos com o preço de custo de cada fornecedor, foto e ficha técnica. Não depende de
 * licitações nem de propostas; usa só o {@code shared} (armazenamento de arquivos, usuário logado).
 */
@ApplicationModule(displayName = "Fornecedores e produtos")
package br.com.shema.crm.catalog;

import org.springframework.modulith.ApplicationModule;
