package br.com.fluxo.web;

import br.com.fluxo.model.*;
import br.com.fluxo.service.FinancasService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api")
public class FinancasController {

    public record SalarioRequest(BigDecimal salario) {}
    public record ValorRequest(BigDecimal valor) {}

    private final FinancasService service;

    public FinancasController(FinancasService service) {
        this.service = service;
    }

    public record PagamentoRequest(Boolean pago) {}
    @PutMapping("/despesas/{id}/pagamento")
    public Painel pagamento(@PathVariable String id,@RequestBody PagamentoRequest req) { return service.pagamento(id,req.pago()); }
    @PostMapping("/entradas") public Painel entrada(@RequestBody Entrada e) { return service.salvarEntrada(null,e); }
    @PutMapping("/entradas/{id}") public Painel entrada(@PathVariable String id,@RequestBody Entrada e) { return service.salvarEntrada(id,e); }
    @DeleteMapping("/entradas/{id}") public Painel excluirEntrada(@PathVariable String id) { return service.excluirEntrada(id); }

    @GetMapping("/painel")
    public Painel painel() { return service.painel(); }

    @GetMapping("/meses")
    public java.util.List<br.com.fluxo.repository.ArmazenamentoJson.ResumoMes> meses() { return service.meses(); }

    @GetMapping("/categorias")
    public List<Categoria> categorias() { return Categoria.CATALOGO; }

    @PutMapping("/salario")
    public Painel salario(@RequestBody SalarioRequest req) { return service.definirSalario(req.salario()); }

    @PutMapping("/percentuais")
    public Painel percentuais(@RequestBody Percentuais p) { return service.definirPercentuais(p); }

    @PutMapping("/essenciais")
    public Painel essenciais(@RequestBody ValorRequest req) { return service.definirEssenciais(req.valor()); }

    @PostMapping("/despesas")
    public Painel criarDespesa(@RequestBody Despesa d) { return service.salvarDespesa(null, d); }

    @PutMapping("/despesas/{id}")
    public Painel editarDespesa(@PathVariable String id, @RequestBody Despesa d) { return service.salvarDespesa(id, d); }

    @DeleteMapping("/despesas/{id}")
    public Painel excluirDespesa(@PathVariable String id) { return service.excluirDespesa(id); }

    @PostMapping("/metas")
    public Painel criarMeta(@RequestBody Meta m) { return service.salvarMeta(null, m); }

    @PutMapping("/metas/{id}")
    public Painel editarMeta(@PathVariable String id, @RequestBody Meta m) { return service.salvarMeta(id, m); }

    @DeleteMapping("/metas/{id}")
    public Painel excluirMeta(@PathVariable String id) { return service.excluirMeta(id); }
}