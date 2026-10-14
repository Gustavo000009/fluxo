package br.com.fluxo.model;

import java.math.BigDecimal;
import java.util.List;

public record Painel(
        boolean configurado,
        String mesAtual,
        BigDecimal salario,
        Percentuais percentuais,
        BigDecimal essenciaManual,
        BigDecimal gastosFixos,
        BigDecimal gastosVariaveis,
        BigDecimal gastos,
        BigDecimal guardado,
        BigDecimal disponivel,
        BigDecimal totalPlanejado,
        BigDecimal totalRegistrado,
        BigDecimal saldoPlanejado,
        BigDecimal compensadoPoupanca,
        BigDecimal excessoSemCobertura,
        double percFixos,
        double percComprometido,
        List<Fatia> distribuicao,
        List<CategoriaTotal> categorias,
        List<Aviso> alertas,
        List<Aviso> recomendacoes,
        Reserva reserva,
        List<MetaCalculada> metas,
        List<Despesa> despesas,
        List<Entrada> entradas,
        BigDecimal totalEntradas,
        BigDecimal rendaTotal,
        java.util.Set<String> despesasPagas) {

    public record Fatia(String chave, BigDecimal percentual, BigDecimal valor, BigDecimal gasto,
                        double uso, int faixaMin, int faixaMax, BigDecimal valorOriginal,
                        int percentualOriginal, BigDecimal ajustePoupanca) {}

    public record CategoriaTotal(String categoria, String icone, String cor, String grupo,
                                 BigDecimal valor, double percentual) {}

    public record Aviso(String tipo, String titulo, String texto) {}

    public record Reserva(BigDecimal essenciais, boolean automatico,
                          BigDecimal tresMeses, BigDecimal seisMeses) {}

    public record MetaCalculada(String id, String nome, BigDecimal valorNecessario,
                                BigDecimal jaTenho, BigDecimal porMes, BigDecimal faltam,
                                double progresso, Integer meses, String texto) {}
}
