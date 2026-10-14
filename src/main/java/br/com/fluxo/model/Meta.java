package br.com.fluxo.model;

import java.math.BigDecimal;

public record Meta(String id, String nome, BigDecimal valorNecessario,
                   BigDecimal jaTenho, BigDecimal porMes) {}