package br.com.fluxo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Tudo que é salvo no arquivo JSON. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Dados {
    public List<Entrada> entradas = new ArrayList<>();
    public java.util.Set<String> despesasPagas = new java.util.HashSet<>();
    public boolean historicoIncompleto;
    public BigDecimal salario = BigDecimal.ZERO;
    public Percentuais percentuais = new Percentuais(55, 20, 15, 10);
    public BigDecimal essenciaMensal; // null = usar o cálculo automático
    public List<Despesa> despesas = new ArrayList<>();
    public List<Meta> metas = new ArrayList<>();
}