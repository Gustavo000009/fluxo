package br.com.fluxo.pagamento;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;

@RestController @RequestMapping("/api/pagamentos")
public class PagamentosController {
    public record Confirmacao(String pedido,String transacao,String slug) {}
    private final Pagamentos pagamentos;
    public PagamentosController(Pagamentos pagamentos){this.pagamentos=pagamentos;}
    @GetMapping("/situacao") public Pagamentos.Situacao situacao(Principal p){return pagamentos.situacao(p.getName());}
    @PostMapping("/checkout") public Pagamentos.Checkout checkout(Principal p){return pagamentos.criar(p.getName());}
    @PostMapping("/confirmar") public Pagamentos.Situacao confirmar(Principal p,@RequestBody Confirmacao c){return pagamentos.confirmar(p.getName(),c.pedido(),c.transacao(),c.slug());}
    @PostMapping("/infinitepay/webhook/{pedido}/{segredo}") public Map<String,Boolean> webhook(@PathVariable String pedido,@PathVariable String segredo,@RequestBody JsonNode corpo){
        pagamentos.webhook(pedido,segredo,corpo.path("transaction_nsu").asText(null),corpo.path("invoice_slug").asText(null),corpo.path("order_nsu").asText(null));
        return Map.of("success",true);
    }
}
