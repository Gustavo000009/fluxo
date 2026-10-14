package br.com.fluxo.repository;

import br.com.fluxo.model.*;
import br.com.fluxo.auth.Contas;
import br.com.fluxo.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import java.nio.file.*;
import java.time.YearMonth;
import java.util.*;

/** Mantém compatibilidade com arquivos antigos e grava novos registros no banco. */
@Repository
public class ArmazenamentoJson {
    private final ObjectMapper mapper;
    private final Path legado;
    private final Contas contas;
    private final BancoDados banco;
    public ArmazenamentoJson(ObjectMapper mapper,@Value("${app.arquivo:dados/financas.json}") String caminho,Contas contas,BancoDados banco) {
        this.mapper=mapper; this.legado=Path.of(caminho); this.contas=contas; this.banco=banco;
    }
    private String usuario() {
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !auth.isAuthenticated() || auth.getPrincipal() instanceof String) throw new org.springframework.security.access.AccessDeniedException("Entre na sua conta.");
        return auth.getName();
    }
    private String dono() { return contas.buscar(usuario()).id(); }
    private Path arquivo(String dono) { return legado.toAbsolutePath().getParent().resolve("usuarios").resolve(dono+".json"); }
    private String json(Dados dados) {
        try { return mapper.writeValueAsString(dados); }
        catch(Exception e) { throw new IllegalStateException("Não foi possível salvar o mês.",e); }
    }
    private Dados dados(String json) {
        try { return mapper.readValue(json,Dados.class); }
        catch(Exception e) { throw new IllegalStateException("Não foi possível ler o mês.",e); }
    }
    private void migrar(String dono,Path arquivo) {
        try {
            Map<String,String> novos=new TreeMap<>();
            if(Files.exists(arquivo)) {
                Dados antigo=mapper.readValue(arquivo.toFile(),Dados.class);
                var atual=YearMonth.now();
                var meses=new TreeSet<YearMonth>(); meses.add(atual);
                antigo.despesas.stream().filter(d->"VARIAVEL".equals(d.tipo())).forEach(d->meses.add(YearMonth.from(d.data())));
                for(var mes:meses) {
                    Dados item=mes.equals(atual)?dados(json(antigo)):new Dados();
                    // O formato antigo não registrava a renda e o plano de meses anteriores.
                    item.historicoIncompleto=!mes.equals(atual);
                    item.despesas=new ArrayList<>(antigo.despesas.stream().filter(d->
                        ("FIXA".equals(d.tipo()) && mes.equals(atual)) || ("VARIAVEL".equals(d.tipo()) && YearMonth.from(d.data()).equals(mes))).toList());
                    novos.put(mes.toString(),json(item));
                }
            }
            novos.put("migrado","true"); banco.salvar(dono,novos);
        } catch(Exception e) { throw new IllegalStateException("Não foi possível migrar os dados. Os arquivos originais foram preservados.",e); }
    }
    private void preparar(String dono) { if(banco.ler(dono,"migrado")==null) migrar(dono,arquivo(dono)); }
    public synchronized Dados carregar() {
        String dono=dono(); preparar(dono);
        var mes=MesSelecionado.atual(); String valor=banco.ler(dono,mes.toString());
        if(valor!=null) return dados(valor);
        Dados novo=new Dados();
        // Só herda um plano real anterior; nunca copia faturas ou gastos variáveis.
        var anteriores=banco.listar(dono).entrySet().stream().filter(e->e.getKey().matches("[0-9]{4}-[0-9]{2}") && e.getKey().compareTo(mes.toString())<0).toList();
        if(!anteriores.isEmpty()) {
            Dados anterior=dados(anteriores.get(anteriores.size()-1).getValue());
            if(!anterior.historicoIncompleto) {
                novo=dados(json(anterior));
                novo.entradas.clear(); novo.despesasPagas.clear();
                novo.despesas=new ArrayList<>(anterior.despesas.stream().filter(d->"FIXA".equals(d.tipo())).map(d->new Despesa(d.id(),d.tipo(),d.nome(),d.categoria(),d.valor(),mes.atDay(Math.min(d.data().getDayOfMonth(),mes.lengthOfMonth())))).toList());
            }
        }
        if(novo.salario.signum()>0) banco.salvar(dono,Map.of(mes.toString(),json(novo)));
        return novo;
    }
    public synchronized void salvar(Dados dados) { String dono=dono(); preparar(dono); banco.salvar(dono,Map.of(MesSelecionado.atual().toString(),json(dados))); }
    public record ResumoMes(String mes,java.math.BigDecimal salario,boolean historicoIncompleto) {}
    public synchronized List<ResumoMes> meses() {
        String dono=dono(); preparar(dono);
        return banco.listar(dono).entrySet().stream().filter(e->e.getKey().matches("[0-9]{4}-[0-9]{2}"))
            .map(e->{var d=dados(e.getValue());return new ResumoMes(e.getKey(),d.salario,d.historicoIncompleto);})
            .sorted(Comparator.comparing(ResumoMes::mes).reversed()).toList();
    }
    public synchronized void importarLegado() {
        String email=usuario(),dono=dono();
        if(!contas.buscar(email).importarLegado()) throw new RegraNegocioException("A importação está disponível somente para a primeira conta local.");
        if(Files.exists(arquivo(dono)) || banco.listar(dono).keySet().stream().anyMatch(k->k.matches("[0-9]{4}-[0-9]{2}"))) throw new RegraNegocioException("Esta conta já possui dados. A importação não sobrescreve dados existentes.");
        if(!Files.exists(legado)) throw new RegraNegocioException("Não há dados antigos para importar.");
        migrar(dono,legado); contas.concluirImportacao(email);
    }
}
