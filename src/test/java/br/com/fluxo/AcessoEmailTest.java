package br.com.fluxo;
import br.com.fluxo.auth.*;
import br.com.fluxo.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpSession;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
@SpringBootTest @AutoConfigureMockMvc
class AcessoEmailTest {
 static Path pasta;
 static {try {pasta=Files.createTempDirectory("fluxo-email-");}catch(Exception e){throw new RuntimeException(e);}}
 @DynamicPropertySource static void props(DynamicPropertyRegistry p){p.add("app.contas",()->pasta.resolve("contas.json").toString());p.add("app.arquivo",()->pasta.resolve("financas.json").toString());}
 @Autowired MockMvc mvc; @Autowired Contas contas; @Autowired BancoDados banco; @Autowired ObjectMapper mapper; @Autowired PasswordEncoder encoder;
 String token(Contas.Conta conta,String tipo,long validade) throws Exception {
  String segredo=Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.SecureRandom.getSeed(32));
  String chave="token-"+AcessoEmail.hash(segredo);
  banco.salvar("sistema",Map.of(chave,mapper.writeValueAsString(new AcessoEmail.Token(conta.email(),tipo,validade,conta.senhaHash())),"ultimo-"+tipo+"-"+conta.id(),chave));return segredo;
 }
 String body(String token,String senha)throws Exception{return mapper.writeValueAsString(Map.of("token",token,"senha",senha));}
 @Test void redefineUmaVezERevogaSessaoAnterior() throws Exception {
  var c=contas.cadastrar("Pessoa Reset","reset@teste.com","senha-antiga-123");
  MockHttpSession sessao=new MockHttpSession();sessao.setAttribute("fluxo.authAt",System.currentTimeMillis()-10000);
  String t=token(c,"senha",System.currentTimeMillis()+60000);
  mvc.perform(post("/api/auth/redefinir").with(csrf()).contentType("application/json").content(body(t,"curta"))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/auth/redefinir").with(csrf()).contentType("application/json").content(body(t,"senha-nova-456"))).andExpect(status().isOk());
  assertTrue(encoder.matches("senha-nova-456",contas.buscar(c.email()).senhaHash()));
  assertFalse(encoder.matches("senha-antiga-123",contas.buscar(c.email()).senhaHash()));
  mvc.perform(post("/api/auth/redefinir").with(csrf()).contentType("application/json").content(body(t,"senha-outra-789"))).andExpect(status().isBadRequest());
  mvc.perform(get("/api/auth/perfil").session(sessao).with(user(c.email()))).andExpect(status().isUnauthorized());
 }
 @Test void confirmacaoRecusaExpiradoFinalidadeErradaEReuso() throws Exception {
  var c=contas.cadastrar("Pessoa Email","email@teste.com","senha-segura-123");
  String expirado=token(c,"email",System.currentTimeMillis()-1000);
  mvc.perform(post("/api/auth/confirmar-email").with(csrf()).contentType("application/json").content(body(expirado,""))).andExpect(status().isBadRequest());
  String t=token(c,"email",System.currentTimeMillis()+60000);
  mvc.perform(post("/api/auth/redefinir").with(csrf()).contentType("application/json").content(body(t,"senha-nova-456"))).andExpect(status().isBadRequest());
  assertFalse(contas.perfil(c.email()).emailConfirmado());
  mvc.perform(post("/api/auth/confirmar-email").contentType("application/json").content(body(t,""))).andExpect(status().isForbidden());
  mvc.perform(post("/api/auth/confirmar-email").with(csrf()).contentType("application/json").content(body(t,""))).andExpect(status().isOk());
  assertTrue(contas.perfil(c.email()).emailConfirmado());
  mvc.perform(post("/api/auth/confirmar-email").with(csrf()).contentType("application/json").content(body(t,""))).andExpect(status().isBadRequest());
 }
}
