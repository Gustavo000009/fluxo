package br.com.fluxo.auth;
import br.com.fluxo.repository.ArmazenamentoJson;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;
import java.security.Principal;
import java.util.Map;
@RestController
@RequestMapping("/api/auth")
public class ContaController {
    public record Cadastro(String nome, String email, String senha, Boolean aceitouTermos, Boolean cienteAssinatura, String versaoTermos) {}
    private final AcessoEmail acessoEmail;
    private final Contas contas;
    private final ArmazenamentoJson armazenamento;
    private final org.springframework.security.web.authentication.RememberMeServices loginPersistente;
    public ContaController(AcessoEmail acessoEmail, Contas contas, ArmazenamentoJson armazenamento, org.springframework.security.web.authentication.RememberMeServices loginPersistente) { this.acessoEmail=acessoEmail; this.contas = contas; this.armazenamento = armazenamento; this.loginPersistente = loginPersistente; }
    public record GestaoConta(String senha,String confirmacao) {}
    @PostMapping("/congelar") public Map<String,String> congelar(Principal p,@RequestBody GestaoConta req){contas.congelar(p.getName(),req.senha(),true);return Map.of("mensagem","Conta congelada. Seus registros foram preservados.");}
    @PostMapping("/reativar") public Map<String,String> reativar(Principal p,@RequestBody GestaoConta req){contas.congelar(p.getName(),req.senha(),false);return Map.of("mensagem","Conta reativada.");}
    @DeleteMapping("/conta") public Map<String,String> excluir(Principal p,@RequestBody GestaoConta corpo,jakarta.servlet.http.HttpServletRequest req,jakarta.servlet.http.HttpServletResponse res){
        contas.excluir(p.getName(),corpo.senha(),corpo.confirmacao());
        var session=req.getSession(false);if(session!=null)session.invalidate();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        for(String nome:java.util.List.of("fluxo-login","JSESSIONID")){var cookie=new jakarta.servlet.http.Cookie(nome,"");cookie.setPath("/");cookie.setHttpOnly(true);cookie.setSecure(req.isSecure());cookie.setMaxAge(0);res.addCookie(cookie);}
        return Map.of("mensagem","Conta excluída.");
    }
    @PutMapping("/foto") public Contas.Perfil foto(Principal p,@RequestParam("foto") org.springframework.web.multipart.MultipartFile foto) { return contas.foto(p.getName(),FotoPerfil.processar(foto)); }
    @DeleteMapping("/foto") public Contas.Perfil removerFoto(Principal p) { return contas.foto(p.getName(),null); }
    @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken(),"header",token.getHeaderName()); }
    @PostMapping("/cadastro") public Map<String,String> cadastro(@RequestBody Cadastro req,jakarta.servlet.http.HttpServletRequest http) {
        var aceite=Termos.validar(req.aceitouTermos(),req.cienteAssinatura(),req.versaoTermos());
        contas.cadastrar(req.nome(),req.email(),req.senha(),aceite);
        if(acessoEmail.disponivel()) {
            try { acessoEmail.solicitar(req.email(),"email",http.getRemoteAddr()); }
            catch(br.com.fluxo.service.RegraNegocioException ignored) { /* A conta existe; o usuário pode reenviar pelo perfil. */ }
        }
        return Map.of("mensagem","Conta criada. Entre para continuar.");
    }
    @GetMapping("/perfil") public Contas.Perfil perfil(Principal p, jakarta.servlet.http.HttpServletRequest req, jakarta.servlet.http.HttpServletResponse res) {
        boolean temLogin = req.getCookies() != null && java.util.Arrays.stream(req.getCookies()).anyMatch(c -> c.getName().equals("fluxo-login") && !c.getValue().isBlank());
        if (!temLogin) loginPersistente.loginSuccess(req, res, org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
        return contas.perfil(p.getName());
    }
    @PostMapping("/comecar-novo") public Map<String,String> comecarNovo(Principal p) { contas.escolherInicio(p.getName(), "NOVO"); return Map.of("mensagem", "Escolha salva."); }
    @PostMapping("/importar") public Map<String,String> importar(Principal p) { armazenamento.importarLegado(); return Map.of("mensagem","Dados antigos importados."); }
}