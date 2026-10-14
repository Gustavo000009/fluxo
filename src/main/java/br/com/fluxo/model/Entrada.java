package br.com.fluxo.model;
import java.math.BigDecimal;
import java.time.LocalDate;
public record Entrada(String id,String nome,BigDecimal valor,LocalDate data) {}
