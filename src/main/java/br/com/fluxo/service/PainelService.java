package br.com.fluxo.service;

import br.com.fluxo.model.*;
import br.com.fluxo.model.Painel.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Stream;

@Service
public class PainelService {

    private static final Locale BR = Locale.forLanguageTag("pt-BR");
    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    public Painel calcular(Dados d) {
        BigDecimal salarioBase = d.salario == null ? BigDecimal.ZERO : d.salario;
        BigDecimal extras = d.entradas.stream().map(Entrada::valor).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal salario = salarioBase.add(extras);
        Percentuais p = d.percentuais;
        YearMonth mes = MesSelecionado.atual();

        // Fixos contam todo mês; variáveis só as do mês atual.
        List<Despesa> doMes = d.despesas.stream()
                .filter(x -> "FIXA".equals(x.tipo()) || YearMonth.from(x.data()).equals(mes))
                .toList();

        // Aportes são registrados separadamente dos gastos de consumo.
        List<Despesa> despesas = doMes.stream()
                .filter(x -> !"POUPANCA".equals(Categoria.buscar(x.categoria()).grupo())).toList();
        BigDecimal fixos = somar(despesas.stream().filter(x -> "FIXA".equals(x.tipo())));
        BigDecimal variaveis = somar(despesas.stream().filter(x -> "VARIAVEL".equals(x.tipo())));
        BigDecimal gastos = fixos.add(variaveis);
        BigDecimal gastoNec = totalGrupo(doMes, "NECESSIDADES");
        BigDecimal gastoLazer = totalGrupo(doMes, "LAZER");
        BigDecimal guardado = totalGrupo(doMes, "POUPANCA");
        BigDecimal gastoEmerg = totalGrupo(doMes, "EMERGENCIA");

        BigDecimal[] originais = distribuir(salario, p);
        BigDecimal[] excessos = {
                gastoNec.subtract(originais[0]).max(BigDecimal.ZERO), BigDecimal.ZERO,
                gastoLazer.subtract(originais[2]).max(BigDecimal.ZERO),
                gastoEmerg.subtract(originais[3]).max(BigDecimal.ZERO)
        };
        BigDecimal excessoTotal = Arrays.stream(excessos).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal poupancaNaoAportada = originais[1].subtract(guardado).max(BigDecimal.ZERO);
        BigDecimal compensado = excessoTotal.min(poupancaNaoAportada);
        BigDecimal semCobertura = excessoTotal.subtract(compensado);
        // Recalcula a partir do plano original: editar/excluir restaura a poupança sem descontos duplicados.
        BigDecimal[] ajustes = ratear(compensado, excessos);
        ajustes[1] = compensado.negate();
        BigDecimal[] valores = new BigDecimal[4];
        for (int i = 0; i < 4; i++) valores[i] = originais[i].add(ajustes[i]);
        BigDecimal[] percentuaisAtuais = ratear(salario.signum() > 0 ? CEM : BigDecimal.ZERO, valores);
        BigDecimal vNec = valores[0], vGuardar = valores[1], vLazer = valores[2], vEmerg = valores[3];
        List<Fatia> distribuicao = List.of(
                new Fatia("necessidades", percentuaisAtuais[0], vNec, gastoNec, pct(gastoNec, vNec), 50, 60, originais[0], p.necessidades(), ajustes[0]),
                new Fatia("guardar", percentuaisAtuais[1], vGuardar, guardado, pct(guardado, vGuardar), 20, 20, originais[1], p.guardar(), ajustes[1]),
                new Fatia("lazer", percentuaisAtuais[2], vLazer, gastoLazer, pct(gastoLazer, vLazer), 10, 20, originais[2], p.lazer(), ajustes[2]),
                new Fatia("emergencia", percentuaisAtuais[3], vEmerg, gastoEmerg, pct(gastoEmerg, vEmerg), 10, 10, originais[3], p.emergencia(), ajustes[3]));
        BigDecimal planejado = distribuicao.stream().map(Fatia::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal registrado = gastos.add(guardado);
        BigDecimal saldoPlanejado = distribuicao.stream().map(f -> f.valor().subtract(f.gasto()).max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Mesmo após a compensação, todo a renda continua destinado aos quatro blocos.
        BigDecimal disponivel = salario.subtract(planejado);
        List<CategoriaTotal> categorias = totaisPorCategoria(doMes, registrado);
        List<Aviso> alertas = new ArrayList<>();
        List<Aviso> recomendacoes = new ArrayList<>();
        if (salario.signum() > 0) {
            recomendacoes.add(new Aviso("boa", "Toda a renda já tem destino",
                    "Os " + brl(planejado) + " da sua renda foram distribuídos entre Necessidades, Poupança, Lazer e Emergência. "
                    + "O valor ainda não utilizado continua reservado nesses blocos; não é dinheiro livre para gastar."));
            if (compensado.signum() > 0) {
                recomendacoes.add(new Aviso("sugestao", "Poupança ajustada automaticamente",
                        "Os gastos acima do plano original foram compensados com " + brl(compensado)
                        + " da poupança ainda não aportada. O planejamento da Poupança passou de "
                        + brl(originais[1]) + " (" + p.guardar() + "%) para " + brl(vGuardar)
                        + " (" + percentuaisAtuais[1].toPlainString().replace('.', ',') + "%). Editar ou excluir os gastos recalcula essa compensação."));
            }
            if (semCobertura.signum() > 0) {
                alertas.add(new Aviso("vermelho", "Poupança insuficiente para cobrir o excesso",
                        "Ainda faltam " + brl(semCobertura) + " para cobrir os gastos acima do plano original. "
                        + "A compensação utiliza somente a poupança ainda não aportada; os aportes registrados são preservados. Revise os gastos ou os percentuais."));
            }
            if (registrado.signum() == 0) {
                recomendacoes.add(new Aviso("sugestao", "Registre os valores reais",
                        "Use Registrar valor em cada bloco para informar suas despesas e os aportes feitos na poupança. Planejar um valor não significa que ele já foi gasto ou guardado."));
            }
            for (Fatia f : distribuicao) {
                String nome = switch (f.chave()) {
                    case "necessidades" -> "Necessidades";
                    case "guardar" -> "Poupança";
                    case "lazer" -> "Lazer";
                    default -> "Emergência";
                };
                BigDecimal excesso = f.gasto().subtract(f.valor());
                if (excesso.signum() > 0) {
                    alertas.add(new Aviso("amarelo", nome + " acima do planejado",
                            "Você registrou " + brl(f.gasto()) + " para um planejamento de " + brl(f.valor())
                            + ". A diferença é de " + brl(excesso) + ". Revise os lançamentos ou os percentuais; a compensação disponível da poupança já foi considerada."));
                } else if (!"guardar".equals(f.chave()) && f.uso() >= 90) {
                    alertas.add(new Aviso("amarelo", nome + ": acompanhe o limite",
                            "Você já utilizou " + num(f.uso()) + "% do valor planejado para este bloco."));
                }
            }
            if (registrado.compareTo(salario) > 0) {
                alertas.add(new Aviso("vermelho", "Lançamentos acima da renda",
                        "Os gastos e aportes registrados somam " + brl(registrado) + ", ultrapassando a renda em "
                        + brl(registrado.subtract(salario)) + ". Revise os valores e ajuste seu planejamento."));
            }
            if (vGuardar.signum() > 0) {
                if (guardado.compareTo(vGuardar) >= 0) {
                    alertas.add(new Aviso("verde", "Poupança registrada",
                            "Você registrou " + brl(guardado) + " em aportes neste mês, atingindo o valor atual planejado de " + brl(vGuardar) + "."));
                } else {
                    recomendacoes.add(new Aviso("sugestao", "Complete sua poupança",
                            "Você planejou " + brl(vGuardar) + " e registrou " + brl(guardado) + " em aportes. Faltam "
                            + brl(vGuardar.subtract(guardado)) + " para completar o planejado neste mês."));
                }
            }
            BigDecimal somaMetas = d.metas.stream().map(Meta::porMes).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (somaMetas.compareTo(vGuardar) > 0) {
                recomendacoes.add(new Aviso("sugestao", "Metas e poupança",
                        "Suas metas preveem " + brl(somaMetas) + " por mês, acima dos " + brl(vGuardar)
                        + " planejados para Poupança. Ajuste as metas ou os percentuais. Os aportes registrados não alteram automaticamente o saldo das metas."));
            }
        }
        boolean automatico = d.essenciaMensal == null;
        BigDecimal essenciais = automatico ? gastoNec : d.essenciaMensal;
        Reserva reserva = new Reserva(essenciais, automatico, essenciais.multiply(BigDecimal.valueOf(3)), essenciais.multiply(BigDecimal.valueOf(6)));
        List<MetaCalculada> metas = d.metas.stream().map(this::calcularMeta).toList();
        return new Painel(salario.signum() > 0, mes.toString(), salarioBase, p, d.essenciaMensal,
                fixos, variaveis, gastos, guardado, disponivel, planejado, registrado, saldoPlanejado, compensado, semCobertura,
                pct(fixos, salario), pct(registrado, salario),
                distribuicao, categorias, alertas, recomendacoes, reserva, metas, d.despesas, d.entradas, extras, salario, d.despesasPagas);
    }

    private static BigDecimal totalGrupo(List<Despesa> itens, String grupo) {
        return somar(itens.stream().filter(x -> grupo.equals(Categoria.buscar(x.categoria()).grupo())));
    }

    // Rateio proporcional por maiores restos, com centavos exatos e sem depender da ordem dos lançamentos.
    private static BigDecimal[] ratear(BigDecimal total, BigDecimal[] pesos) {
        BigDecimal[] resultado = new BigDecimal[pesos.length];
        Arrays.fill(resultado, BigDecimal.ZERO.setScale(2));
        BigInteger unidades = total.setScale(2, RoundingMode.HALF_UP).movePointRight(2).toBigIntegerExact();
        BigInteger[] inteiros = Arrays.stream(pesos)
                .map(v -> v.setScale(2, RoundingMode.HALF_UP).movePointRight(2).toBigIntegerExact()).toArray(BigInteger[]::new);
        BigInteger soma = Arrays.stream(inteiros).reduce(BigInteger.ZERO, BigInteger::add);
        if (unidades.signum() == 0 || soma.signum() == 0) return resultado;
        BigInteger usados = BigInteger.ZERO;
        BigInteger[] restos = new BigInteger[pesos.length];
        for (int i = 0; i < pesos.length; i++) {
            BigInteger[] divisao = unidades.multiply(inteiros[i]).divideAndRemainder(soma);
            resultado[i] = new BigDecimal(divisao[0], 2);
            restos[i] = divisao[1];
            usados = usados.add(divisao[0]);
        }
        int restantes = unidades.subtract(usados).intValueExact();
        for (int c = 0; c < restantes; c++) {
            int maior = 0;
            for (int i = 1; i < restos.length; i++) if (restos[i].compareTo(restos[maior]) > 0) maior = i;
            resultado[maior] = resultado[maior].add(new BigDecimal("0.01"));
            restos[maior] = BigInteger.valueOf(-1);
        }
        return resultado;
    }

    // Distribui também os centavos, sem criar sobra por arredondamento.
    private static BigDecimal[] distribuir(BigDecimal salario, Percentuais p) {
        int[] percentuais = {p.necessidades(), p.guardar(), p.lazer(), p.emergencia()};
        BigDecimal[] valores = new BigDecimal[4], fracoes = new BigDecimal[4];
        BigDecimal soma = BigDecimal.ZERO;
        for (int i = 0; i < 4; i++) {
            BigDecimal exato = salario.multiply(BigDecimal.valueOf(percentuais[i])).divide(CEM);
            valores[i] = exato.setScale(2, RoundingMode.DOWN);
            fracoes[i] = exato.subtract(valores[i]);
            soma = soma.add(valores[i]);
        }
        int centavos = salario.subtract(soma).movePointRight(2).intValue();
        for (int c = 0; c < centavos; c++) {
            int maior = 0;
            for (int i = 1; i < 4; i++) if (fracoes[i].compareTo(fracoes[maior]) > 0) maior = i;
            valores[maior] = valores[maior].add(new BigDecimal("0.01"));
            fracoes[maior] = BigDecimal.valueOf(-1);
        }
        return valores;
    }

    // ---------- Metas ----------

    private MetaCalculada calcularMeta(Meta m) {
        BigDecimal faltam = m.valorNecessario().subtract(m.jaTenho()).max(BigDecimal.ZERO);
        double progresso = Math.min(100, pct(m.jaTenho(), m.valorNecessario()));
        Integer meses = null;
        String texto;
        if (faltam.signum() == 0) {
            texto = "Meta atingida! Você já tem o valor necessário.";
        } else if (m.porMes().signum() <= 0) {
            texto = "Faltam " + brl(faltam) + ". Informe quanto pretende guardar por mês para calcular o prazo.";
        } else {
            meses = faltam.divide(m.porMes(), 0, RoundingMode.CEILING).intValue();
            texto = "Faltam " + brl(faltam) + ". Guardando " + brl(m.porMes())
                    + " por mês, você alcançará sua meta em aproximadamente "
                    + meses + (meses == 1 ? " mês." : " meses.");
        }
        return new MetaCalculada(m.id(), m.nome(), m.valorNecessario(), m.jaTenho(), m.porMes(),
                faltam, progresso, meses, texto);
    }

    // ---------- Auxiliares ----------

    private List<CategoriaTotal> totaisPorCategoria(List<Despesa> doMes, BigDecimal total) {
        Map<String, BigDecimal> mapa = new LinkedHashMap<>();
        for (Despesa x : doMes) {
            mapa.merge(x.categoria(), x.valor(), BigDecimal::add);
        }
        return mapa.entrySet().stream()
                .map(e -> {
                    Categoria c = Categoria.buscar(e.getKey());
                    return new CategoriaTotal(c.nome(), c.icone(), c.cor(), c.grupo(),
                            e.getValue(), pct(e.getValue(), total));
                })
                .sorted((a, b) -> b.valor().compareTo(a.valor()))
                .toList();
    }

    private static BigDecimal somar(Stream<Despesa> despesas) {
        return despesas.map(Despesa::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal parte(BigDecimal salario, int percentual) {
        return salario.multiply(BigDecimal.valueOf(percentual)).divide(CEM, 2, RoundingMode.HALF_UP);
    }

    private static double pct(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() <= 0) return 0;
        return parte.multiply(CEM).divide(total, 1, RoundingMode.HALF_UP).doubleValue();
    }

    private static String brl(BigDecimal v) {
        return NumberFormat.getCurrencyInstance(BR).format(v).replace('\u00A0', ' ');
    }

    private static String num(double v) {
        NumberFormat nf = NumberFormat.getNumberInstance(BR);
        nf.setMaximumFractionDigits(1);
        return nf.format(v);
    }
}
