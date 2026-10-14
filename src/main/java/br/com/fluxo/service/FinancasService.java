package br.com.fluxo.service;

import br.com.fluxo.model.*;
import br.com.fluxo.repository.ArmazenamentoJson;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class FinancasService {

    private final ArmazenamentoJson armazenamento;
    private final PainelService painelService;

    public FinancasService(ArmazenamentoJson armazenamento, PainelService painelService) {
        this.armazenamento = armazenamento;
        this.painelService = painelService;
        
    }

    public java.util.List<ArmazenamentoJson.ResumoMes> meses() { return armazenamento.meses(); }

    public synchronized Painel painel() {
        Dados dados = armazenamento.carregar();
        return painelService.calcular(dados);
    }

    private Painel salvar(Dados dados) {
        armazenamento.salvar(dados);
        return painelService.calcular(dados);
    }

    private static BigDecimal dinheiro(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    // ---------- Salário, porcentagens e reserva ----------

    public synchronized Painel definirSalario(BigDecimal salario) {
        Dados dados = armazenamento.carregar();
        if (salario == null || salario.signum() <= 0) {
            throw new RegraNegocioException("Informe um salário maior que zero.");
        }
        dados.salario = dinheiro(salario);
        return salvar(dados);
    }

    public synchronized Painel definirPercentuais(Percentuais p) {
        Dados dados = armazenamento.carregar();
        if (p == null || p.temNegativo()) {
            throw new RegraNegocioException("As porcentagens não podem ser negativas.");
        }
        if (p.soma() != 100) {
            throw new RegraNegocioException("Suas categorias precisam totalizar 100%.");
        }
        dados.percentuais = p;
        return salvar(dados);
    }

    public synchronized Painel definirEssenciais(BigDecimal valor) {
        Dados dados = armazenamento.carregar();
        dados.essenciaMensal = (valor == null || valor.signum() <= 0) ? null : dinheiro(valor);
        return salvar(dados);
    }

    // ---------- Despesas ----------

    public synchronized Painel salvarDespesa(String id, Despesa e) {
        Dados dados = armazenamento.carregar();
        String tipo = e.tipo() == null ? "" : e.tipo().toUpperCase();
        if (!tipo.equals("FIXA") && !tipo.equals("VARIAVEL")) {
            throw new RegraNegocioException("Tipo de gasto inválido.");
        }
        if (e.nome() == null || e.nome().isBlank()) {
            throw new RegraNegocioException("Informe o nome do gasto.");
        }
        if (e.valor() == null || e.valor().signum() <= 0) {
            throw new RegraNegocioException("Informe um valor maior que zero.");
        }
        if (dinheiro(e.valor()).signum() <= 0) {
            throw new RegraNegocioException("Informe um valor de pelo menos R$ 0,01.");
        }
        LocalDate data = e.data() != null ? e.data() : MesSelecionado.atual().atDay(1);
        if (!java.time.YearMonth.from(data).equals(MesSelecionado.atual())) throw new RegraNegocioException("A data precisa pertencer ao mês selecionado. Troque o mês no histórico para registrar esse valor.");
        Despesa nova = new Despesa(
                id != null ? id : UUID.randomUUID().toString(),
                tipo, e.nome().trim(), Categoria.buscar(e.categoria()).nome(),
                dinheiro(e.valor()), data);

        if (id == null) {
            dados.despesas.add(nova);
        } else {
            boolean[] achou = {false};
            dados.despesas.replaceAll(d -> {
                if (d.id().equals(id)) {
                    achou[0] = true;
                    return nova;
                }
                return d;
            });
            if (!achou[0]) {
                throw new NaoEncontradoException("Gasto não encontrado.");
            }
        }
        return salvar(dados);
    }

    public synchronized Painel excluirDespesa(String id) {
        Dados dados = armazenamento.carregar();
        if (!dados.despesas.removeIf(d -> d.id().equals(id))) {
            throw new NaoEncontradoException("Gasto não encontrado.");
        }
        dados.despesasPagas.remove(id);
        return salvar(dados);
    }

    public synchronized Painel pagamento(String id, Boolean pago) {
        if(pago==null) throw new RegraNegocioException("Informe se o gasto foi pago.");
        Dados dados=armazenamento.carregar();
        var despesa=dados.despesas.stream().filter(d->d.id().equals(id)).findFirst().orElseThrow(()->new NaoEncontradoException("Gasto não encontrado."));
        if("POUPANCA".equals(Categoria.buscar(despesa.categoria()).grupo())) throw new RegraNegocioException("Aportes não são contas a pagar.");
        if(pago) dados.despesasPagas.add(id); else dados.despesasPagas.remove(id);
        return salvar(dados);
    }
    public synchronized Painel salvarEntrada(String id, Entrada entrada) {
        if(entrada.nome()==null || entrada.nome().isBlank() || entrada.nome().length()>100) throw new RegraNegocioException("Informe a origem da entrada, com até 100 caracteres.");
        if(entrada.valor()==null || dinheiro(entrada.valor()).signum()<=0) throw new RegraNegocioException("Informe uma entrada de pelo menos R$ 0,01.");
        if(entrada.data()==null || !java.time.YearMonth.from(entrada.data()).equals(MesSelecionado.atual())) throw new RegraNegocioException("A data precisa pertencer ao mês selecionado.");
        Dados dados=armazenamento.carregar();
        var nova=new Entrada(id==null?UUID.randomUUID().toString():id,entrada.nome().trim(),dinheiro(entrada.valor()),entrada.data());
        if(id==null) dados.entradas.add(nova);
        else {
            int indice=-1; for(int i=0;i<dados.entradas.size();i++) if(dados.entradas.get(i).id().equals(id)) indice=i;
            if(indice<0) throw new NaoEncontradoException("Entrada não encontrada.");
            dados.entradas.set(indice,nova);
        }
        return salvar(dados);
    }
    public synchronized Painel excluirEntrada(String id) {
        Dados dados=armazenamento.carregar();
        if(!dados.entradas.removeIf(e->e.id().equals(id))) throw new NaoEncontradoException("Entrada não encontrada.");
        return salvar(dados);
    }

    // ---------- Metas ----------

    public synchronized Painel salvarMeta(String id, Meta m) {
        Dados dados = armazenamento.carregar();
        if (m.nome() == null || m.nome().isBlank()) {
            throw new RegraNegocioException("Informe o nome da meta.");
        }
        if (m.valorNecessario() == null || m.valorNecessario().signum() <= 0) {
            throw new RegraNegocioException("Informe o valor necessário para a meta.");
        }
        BigDecimal jaTenho = m.jaTenho() == null ? BigDecimal.ZERO : m.jaTenho();
        BigDecimal porMes = m.porMes() == null ? BigDecimal.ZERO : m.porMes();
        if (jaTenho.signum() < 0 || porMes.signum() < 0) {
            throw new RegraNegocioException("Os valores da meta não podem ser negativos.");
        }
        Meta nova = new Meta(id != null ? id : UUID.randomUUID().toString(), m.nome().trim(),
                dinheiro(m.valorNecessario()), dinheiro(jaTenho), dinheiro(porMes));

        if (id == null) {
            dados.metas.add(nova);
        } else {
            boolean[] achou = {false};
            dados.metas.replaceAll(x -> {
                if (x.id().equals(id)) {
                    achou[0] = true;
                    return nova;
                }
                return x;
            });
            if (!achou[0]) {
                throw new NaoEncontradoException("Meta não encontrada.");
            }
        }
        return salvar(dados);
    }

    public synchronized Painel excluirMeta(String id) {
        Dados dados = armazenamento.carregar();
        if (!dados.metas.removeIf(m -> m.id().equals(id))) {
            throw new NaoEncontradoException("Meta não encontrada.");
        }
        return salvar(dados);
    }
}
