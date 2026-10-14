package br.com.fluxo.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** tipo: "FIXA" ou "VARIAVEL". Em despesas fixas, data = vencimento. */
public record Despesa(String id, String tipo, String nome, String categoria,
                      BigDecimal valor, LocalDate data) {}