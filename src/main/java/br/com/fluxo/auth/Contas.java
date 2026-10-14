package br.com.fluxo.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import br.com.fluxo.service.RegraNegocioException;
import java.nio.file.*;
import java.util.*;

@Service
public class Contas implements UserDetailsService {
    public record Conta(String id, String nome, String email, String senhaHash, boolean importarLegado, String inicio) {}
    public record Perfil(String nome,String email,String plano,boolean importarLegado,String foto,String testeAte,int diasRestantes,String precoMensal,boolean emailConfirmado,boolean congelada) {}
    public record Preferencias(String foto,String testeInicio) {}
    @Value("${app.proprietario.id:}") private String proprietarioId="";
    @Value("${app.proprietario.email:}") private String proprietarioEmail="";
    private final ObjectMapper mapper;
    private final PasswordEncoder encoder;
    private final Path arquivo;
    private final br.com.fluxo.repository.BancoDados banco;
    public Contas(ObjectMapper mapper, PasswordEncoder encoder, @Value("${app.contas:dados/contas.json}") String arquivo, br.com.fluxo.repository.BancoDados banco) {
        this.banco = banco; this.mapper = mapper; this.encoder = encoder; this.arquivo = Path.of(arquivo);
    }
    public static String email(String email) { return email == null ? "" : email.trim().toLowerCase(Locale.ROOT); }
    private List<Conta> ler() {
        try {
            String json=banco.ler("sistema","contas");
            if(json == null) {
                json=Files.exists(arquivo)?Files.readString(arquivo):"[]";
                mapper.readValue(json, Conta[].class); // Valida antes de migrar; preserva o original.
                banco.salvar("sistema",Map.of("contas",json));
            }
            return new ArrayList<>(Arrays.asList(mapper.readValue(json,Conta[].class)));
        } catch(Exception e) { throw new IllegalStateException("Não foi possível ler as contas.",e); }
    }
    private void gravar(List<Conta> contas) {
        try { banco.salvar("sistema",Map.of("contas",mapper.writeValueAsString(contas))); }
        catch(Exception e) { throw new IllegalStateException("Não foi possível salvar as contas.",e); }
    }
    public synchronized Conta cadastrar(String nome, String email, String senha) {
        return cadastrar(nome,email,senha,null);
    }
    public synchronized Conta cadastrar(String nome, String email, String senha, Termos.Aceite aceite) {
        email = email(email);
        if (nome == null || !nome.trim().matches("(?s).*\\S+\\s+\\S+.*") || nome.length() > 80) throw new RegraNegocioException("Informe seu nome completo, com nome e sobrenome, até 80 caracteres.");
        if (email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new RegraNegocioException("Informe um e-mail válido.");
        if (senha == null || senha.length() < 10 || senha.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) throw new RegraNegocioException("Use uma senha com pelo menos 10 caracteres e no máximo 72 bytes.");
        List<Conta> contas = ler();
        String normalizado = email;
        if (contas.stream().anyMatch(c -> c.email().equals(normalizado))) throw new RegraNegocioException("Já existe uma conta com este e-mail. Entre na sua conta ou use Esqueci minha senha.");
        boolean primeira=contas.isEmpty() && banco.ler("sistema","cadastro-iniciado")==null;
        Conta conta = new Conta(UUID.randomUUID().toString(), nome.trim().replaceAll("\\s+", " "), email, encoder.encode(senha), primeira, primeira ? null : "NOVO");
        contas.add(conta);
        try {
            var registros=new HashMap<String,String>();
            registros.put("contas",mapper.writeValueAsString(contas));
            registros.put("cadastro-iniciado","true");
            if(aceite!=null)registros.put("aceite-"+conta.id(),mapper.writeValueAsString(aceite));
            banco.salvar("sistema",registros); // Conta e aceite confirmados na mesma transação.
        }catch(Exception e){throw new IllegalStateException("Não foi possível salvar a conta e o aceite.",e);}
        preferencias(conta.id()); return conta;
    }
    public synchronized Conta buscar(String email) {
        return ler().stream().filter(c -> c.email().equals(email(email))).findFirst().orElseThrow(() -> new UsernameNotFoundException("Conta não encontrada."));
    }
    private Preferencias preferencias(String id) {
        try {
            String json=banco.ler(id,"perfil");
            if(json!=null) return mapper.readValue(json,Preferencias.class);
            var inicial=new Preferencias(null,java.time.Instant.now().toString());
            salvarPreferencias(id,inicial); return inicial;
        } catch(Exception e) { throw new IllegalStateException("Não foi possível ler o perfil.",e); }
    }
    private void salvarPreferencias(String id,Preferencias preferencias) {
        try { banco.salvar(id,Map.of("perfil",mapper.writeValueAsString(preferencias))); }
        catch(Exception e) { throw new IllegalStateException("Não foi possível salvar o perfil.",e); }
    }
    public synchronized boolean proprietario(String endereco) {
        Conta conta=buscar(endereco);
        return !proprietarioId.isBlank() && conta.id().equals(proprietarioId) && conta.email().equals(email(proprietarioEmail));
    }
    public synchronized Perfil perfil(String email) {
        Conta c=buscar(email); var prefs=preferencias(c.id());
        var fim=java.time.Instant.parse(prefs.testeInicio()).plus(java.time.Duration.ofDays(7));
        var segundos=java.time.Duration.between(java.time.Instant.now(),fim).getSeconds();
        String pagoAte=banco.ler("pagamentos","acesso-"+c.id());
        boolean pago=pagoAte!=null && java.time.Instant.parse(pagoAte).isAfter(java.time.Instant.now());
        return new Perfil(c.nome(),c.email(),proprietario(email)?"PROPRIETARIO":pago?"ATIVO":segundos>0?"TESTE":"EXPIRADO",c.importarLegado(),prefs.foto(),fim.toString(),(int)Math.max(0,(segundos+86399)/86400),"9,90", "true".equals(banco.ler("sistema","email-"+c.id())),congelada(email));
    }
    public synchronized Perfil foto(String email,String foto) {
        Conta c=buscar(email); var prefs=preferencias(c.id());
        salvarPreferencias(c.id(),new Preferencias(foto,prefs.testeInicio())); return perfil(email);
    }
    public synchronized void concluirImportacao(String email) { escolherInicio(email, "IMPORTAR"); }
    public synchronized void escolherInicio(String email, String inicio) {
        List<Conta> contas = ler();
        contas.replaceAll(c -> c.email().equals(email(email)) ? new Conta(c.id(), c.nome(), c.email(), c.senhaHash(), false, c.inicio() == null ? inicio : c.inicio()) : c);
        gravar(contas);
    }
    public synchronized void redefinirSenha(String email,String senha,String tokenChave) {
        if(senha==null || senha.length()<10 || senha.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) throw new RegraNegocioException("Use uma senha de pelo menos 10 caracteres e até 72 bytes.");
        var lista=ler(); var conta=buscar(email); String hash=encoder.encode(senha);
        lista.replaceAll(c->c.id().equals(conta.id())?new Conta(c.id(),c.nome(),c.email(),hash,c.importarLegado(),c.inicio()):c);
        try { banco.salvar("sistema",Map.of("contas",mapper.writeValueAsString(lista),tokenChave,"usado","revogado-"+conta.id(),Long.toString(System.currentTimeMillis()))); }
        catch(Exception e) {throw new IllegalStateException("Não foi possível redefinir a senha.",e);}
    }
    public synchronized boolean congelada(String email) {return "true".equals(banco.ler("sistema","congelada-"+buscar(email).id()));}
    private Conta confirmarSenha(String email,String senha) {
        var conta=buscar(email);
        if(senha==null || senha.length()>200 || !encoder.matches(senha,conta.senhaHash()))throw new RegraNegocioException("Senha incorreta. Confira sua senha atual.");
        return conta;
    }
    public synchronized void congelar(String email,String senha,boolean congelar) {
        var conta=confirmarSenha(email,senha);
        banco.salvar("sistema",Map.of("congelada-"+conta.id(),Boolean.toString(congelar)));
    }
    public synchronized void excluir(String email,String senha,String confirmacao) {
        var conta=confirmarSenha(email,senha);
        if(!"EXCLUIR".equals(confirmacao))throw new RegraNegocioException("Digite EXCLUIR para confirmar a exclusão definitiva.");
        var lista=ler();lista.removeIf(c->c.id().equals(conta.id()));
        var remover=new HashSet<String>(List.of("email-"+conta.id(),"congelada-"+conta.id(),"ultimo-senha-"+conta.id(),"ultimo-email-"+conta.id()));
        for(var item:banco.listar("sistema").entrySet())if(item.getKey().startsWith("token-") && !item.getValue().equals("usado")) {
            try {if(mapper.readTree(item.getValue()).path("email").asText().equals(conta.email()))remover.add(item.getKey());}catch(Exception ignored){}
        }
        try {
            banco.excluirConta(conta.id(),Map.of("contas",mapper.writeValueAsString(lista),"cadastro-iniciado","true","excluida-"+conta.id(),java.time.Instant.now().toString(),"revogado-email-"+AcessoEmail.hash(conta.email()),Long.toString(System.currentTimeMillis())),remover);
        }catch(Exception e){throw new IllegalStateException("Não foi possível excluir a conta.",e);}
    }
    public long revogadoEm(String email) {
        try {
            String valor=banco.ler("sistema","revogado-"+buscar(email).id());
            String anterior=banco.ler("sistema","revogado-email-"+AcessoEmail.hash(email(email)));
            return Math.max(valor==null?0:Long.parseLong(valor),anterior==null?0:Long.parseLong(anterior));
        }catch(UsernameNotFoundException e){return Long.MAX_VALUE;}
    }
    @Override public UserDetails loadUserByUsername(String email) {
        Conta c = buscar(email);
        return User.withUsername(c.email()).password(c.senhaHash()).roles("USER").build();
    }
}