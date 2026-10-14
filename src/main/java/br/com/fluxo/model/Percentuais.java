package br.com.fluxo.model;

public record Percentuais(int necessidades, int guardar, int lazer, int emergencia) {
    public int soma() {
        return necessidades + guardar + lazer + emergencia;
    }

    public boolean temNegativo() {
        return necessidades < 0 || guardar < 0 || lazer < 0 || emergencia < 0;
    }
}