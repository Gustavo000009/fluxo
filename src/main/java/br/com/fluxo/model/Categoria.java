package br.com.fluxo.model;

import java.util.List;

/** grupo define o bloco do lançamento; POUPANCA representa aportes, não despesas. */
public record Categoria(String nome, String icone, String grupo, String cor) {

    public static final List<Categoria> CATALOGO = List.of(
        new Categoria("Moradia", "🏠", "NECESSIDADES", "#0ea5e9"),
        new Categoria("Contas", "💡", "NECESSIDADES", "#f59e0b"),
        new Categoria("Alimentação", "🍔", "NECESSIDADES", "#f97316"),
        new Categoria("Transporte", "🚗", "NECESSIDADES", "#8b5cf6"),
        new Categoria("Saúde", "🩺", "NECESSIDADES", "#ef4444"),
        new Categoria("Educação", "🎓", "NECESSIDADES", "#14b8a6"),
        new Categoria("Lazer", "🎮", "LAZER", "#ec4899"),
        new Categoria("Restaurantes", "🍕", "LAZER", "#e11d48"),
        new Categoria("Compras", "🛍️", "LAZER", "#6366f1"),
        new Categoria("Assinaturas", "📺", "LAZER", "#0891b2"),
        new Categoria("Reserva financeira", "🛡️", "POUPANCA", "#16805d"),
        new Categoria("Investimentos", "📈", "POUPANCA", "#16805d"),
        new Categoria("Objetivos futuros", "🎯", "POUPANCA", "#16805d"),
        new Categoria("Manutenção imprevista", "🔧", "EMERGENCIA", "#ab731d"),
        new Categoria("Saúde imprevista", "🩺", "EMERGENCIA", "#ab731d"),
        new Categoria("Perda de renda", "💼", "EMERGENCIA", "#ab731d"),
        new Categoria("Outros imprevistos", "⚠️", "EMERGENCIA", "#ab731d"),
        new Categoria("Outros", "📦", "NECESSIDADES", "#64748b")
    );

    public static Categoria buscar(String nome) {
        String n = nome == null ? "" : nome.trim();
        return CATALOGO.stream()
                .filter(c -> c.nome().equalsIgnoreCase(n))
                .findFirst()
                .orElse(CATALOGO.get(CATALOGO.size() - 1));
    }
}
