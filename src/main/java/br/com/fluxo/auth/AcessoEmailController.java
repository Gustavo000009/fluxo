package br.com.fluxo.auth;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.security.Principal;
@RestController @RequestMapping("/api/auth")
public class AcessoEmailController {
    public record Pedido(String email){} public record Confirmacao(String token,String senha){}
    private final AcessoEmail acesso;
    public AcessoEmailController(AcessoEmail acesso){this.acesso=acesso;}
    @PostMapping("/recuperar") public Map<String,String> recuperar(@RequestBody Pedido p,jakarta.servlet.http.HttpServletRequest req){acesso.solicitar(p.email(),"senha",req.getRemoteAddr());return Map.of("mensagem","Se houver uma conta com esse e-mail, você receberá um link para redefinir sua senha.");}
    @PostMapping("/redefinir") public Map<String,String> redefinir(@RequestBody Confirmacao c){acesso.consumir(c.token(),"senha",c.senha());return Map.of("mensagem","Senha alterada. Entre novamente com a nova senha.");}
    @PostMapping("/enviar-confirmacao") public Map<String,String> enviar(Principal p,jakarta.servlet.http.HttpServletRequest req){acesso.solicitar(p.getName(),"email",req.getRemoteAddr());return Map.of("mensagem","Solicitação recebida. Confira seu e-mail em alguns instantes.");}
    @PostMapping("/confirmar-email") public Map<String,String> confirmar(@RequestBody Confirmacao c){acesso.consumir(c.token(),"email",null);return Map.of("mensagem","E-mail confirmado.");}
}
