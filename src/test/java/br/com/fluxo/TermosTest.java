package br.com.fluxo;
import br.com.fluxo.auth.*;
import br.com.fluxo.repository.BancoDados;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest @AutoConfigureMockMvc
class TermosTest {
 static Path pasta;
 static {try{pasta=Files.createTempDirectory("fluxo-termos-");}catch(Exception e){throw new RuntimeException(e);}}
 @DynamicPropertySource static void props(DynamicPropertyRegistry p){p.add("app.contas",()->pasta.resolve("contas.json").toString());p.add("app.arquivo",()->pasta.resolve("financas.json").toString());}
 @Autowired MockMvc mvc; @Autowired Contas contas; @Autowired BancoDados banco; @Autowired ObjectMapper mapper;
 Map<String,Object> cadastro(String email){var p=new HashMap<String,Object>();p.put("nome","Pessoa Teste");p.put("email",email);p.put("senha","senha-segura-123");return p;}
 @Test void servidorRecusaAusenciaRecusaParcialEVersaoAntiga()throws Exception {
  String email="sem-aceite@teste.com";
  for(var aceite:List.of(Map.of(),Map.of("aceitouTermos",true),Map.of("aceitouTermos",true,"cienteAssinatura",false,"versaoTermos",Termos.VERSAO),Map.of("aceitouTermos",false,"cienteAssinatura",true,"versaoTermos",Termos.VERSAO),Map.of("aceitouTermos",true,"cienteAssinatura",true,"versaoTermos","antiga"))) {
   var p=cadastro(email);aceite.forEach((k,v)->p.put(k.toString(),v));
   mvc.perform(post("/api/auth/cadastro").with(csrf()).contentType("application/json").content(mapper.writeValueAsString(p))).andExpect(status().isBadRequest());
   assertThrows(org.springframework.security.core.userdetails.UsernameNotFoundException.class,()->contas.buscar(email));
  }
 }
 @Test void registraAceiteAtualSemAutorizarDebitoNemGuardarSenha()throws Exception {
  String email="com-aceite@teste.com";var p=cadastro(email);p.putAll(Map.of("aceitouTermos",true,"cienteAssinatura",true,"versaoTermos",Termos.VERSAO));
  Instant antes=Instant.now();
  mvc.perform(post("/api/auth/cadastro").with(csrf()).contentType("application/json").content(mapper.writeValueAsString(p))).andExpect(status().isOk());
  var c=contas.buscar(email);String salvo=banco.ler("sistema","aceite-"+c.id());var aceite=mapper.readValue(salvo,Termos.Aceite.class);
  assertEquals(Termos.VERSAO,aceite.versao());assertEquals(Termos.DOCUMENTO,aceite.documento());
  assertEquals(Termos.validar(true,true,Termos.VERSAO).sha256(),aceite.sha256());
  assertTrue(aceite.aceitouTermos());assertTrue(aceite.cienteAssinatura());assertFalse(aceite.autorizaDebitoAutomatico());assertEquals(990,aceite.valorMensalCentavos());assertEquals(7,aceite.diasGratis());
  assertFalse(Instant.parse(aceite.aceitoEm()).isBefore(antes));assertFalse(salvo.contains("senha-segura-123"));
  mvc.perform(post("/api/auth/cadastro").with(csrf()).contentType("application/json").content(mapper.writeValueAsString(p))).andExpect(status().isBadRequest());
  assertEquals(salvo,banco.ler("sistema","aceite-"+c.id()));
 }
 @Test void documentoAcessivelAntesDoCadastroEContaAntigaNaoRecebeAceiteInventado()throws Exception {
  mvc.perform(get(Termos.DOCUMENTO)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("R$ 9,90"))).andExpect(content().string(org.hamcrest.Matchers.containsString("Gustavo dos Santos Rodrigues")));
  var c=contas.cadastrar("Conta Legada","legado@teste.com","senha-segura-123");
  assertNull(banco.ler("sistema","aceite-"+c.id()));
 }
}
