package br.com.fluxo.auth;
import br.com.fluxo.repository.BancoDados;
import br.com.fluxo.service.RegraNegocioException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
@Service
public class AcessoEmail {
    public record Token(String email,String tipo,long expira,String senhaAtual) {}
    private final Contas contas;private final BancoDados banco;private final ObjectMapper mapper;private final EmailFluxo email;
    private final ThreadPoolExecutor fila=new ThreadPoolExecutor(1,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(100));
    private final Map<String,Long> limites=new HashMap<>();
    public AcessoEmail(Contas contas,BancoDados banco,ObjectMapper mapper,EmailFluxo email){this.contas=contas;this.banco=banco;this.mapper=mapper;this.email=email;}
    public static String hash(String valor) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    public boolean disponivel(){return email.disponivel();}
    public synchronized void solicitar(String endereco,String tipo,String ip) {
        if(!disponivel())throw new RegraNegocioException("O envio de e-mails ainda está em configuração. Tente novamente mais tarde.");
        long agora=System.currentTimeMillis();limites.entrySet().removeIf(e->e.getValue()<agora);
        String chave=tipo+hash(Contas.email(endereco)), origem="ip-"+hash(ip);
        if(limites.containsKey(chave)||limites.containsKey(origem))return;
        if(limites.size()>20000)return;
        limites.put(chave,agora+60000);limites.put(origem,agora+3000);
        try { fila.execute(()->enfileirar(Contas.email(endereco),tipo)); }
        catch(RejectedExecutionException e){throw new RegraNegocioException("Tente novamente em alguns minutos.");}
    }
    private synchronized void enfileirar(String endereco,String tipo) {
        try {
            var conta=contas.buscar(endereco);
            if(tipo.equals("email") && contas.perfil(endereco).emailConfirmado())return;
            byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
            String segredo=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),chave="token-"+hash(segredo);
            var token=new Token(endereco,tipo,System.currentTimeMillis()+(tipo.equals("senha")?1800000L:86400000L),conta.senhaHash());
            banco.salvar("sistema",Map.of(chave,mapper.writeValueAsString(token),"ultimo-"+tipo+"-"+conta.id(),chave));
            email.enviar(endereco,tipo,segredo);
        } catch(org.springframework.security.core.userdetails.UsernameNotFoundException ignored) {
        } catch(Exception e) {org.slf4j.LoggerFactory.getLogger(AcessoEmail.class).warn("Não foi possível enviar uma mensagem de acesso. Confira a configuração de e-mail.");}
    }
    public synchronized void consumir(String segredo,String tipo,String senha) {
        if(segredo==null || !segredo.matches("[A-Za-z0-9_-]{43}"))throw invalido();
        String chave="token-"+hash(segredo),json=banco.ler("sistema",chave);
        try {
            if(json==null || json.equals("usado"))throw invalido();
            var token=mapper.readValue(json,Token.class);var conta=contas.buscar(token.email());
            if(!token.tipo().equals(tipo)||token.expira()<=System.currentTimeMillis()||!conta.senhaHash().equals(token.senhaAtual())||!chave.equals(banco.ler("sistema","ultimo-"+tipo+"-"+conta.id())))throw invalido();
            if(tipo.equals("senha"))contas.redefinirSenha(token.email(),senha,chave);
            else banco.salvar("sistema",Map.of(chave,"usado","email-"+conta.id(),"true"));
        } catch(RegraNegocioException e){throw e;}catch(Exception e){throw invalido();}
    }
    private RegraNegocioException invalido(){return new RegraNegocioException("Este link expirou ou já foi usado. Solicite um novo.");}
    @jakarta.annotation.PreDestroy public void fechar(){fila.shutdownNow();}
}
