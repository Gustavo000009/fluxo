package br.com.fluxo;
import br.com.fluxo.auth.*;
import br.com.fluxo.pagamento.InfinitePayClient;
import br.com.fluxo.repository.BancoDados;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(properties={"app.proprietario.id=11111111-1111-1111-1111-111111111111","app.proprietario.email=dono@teste.com","app.infinitepay.habilitado=true","app.url-publica=https://fluxo.example"})
@AutoConfigureMockMvc
class ProprietarioTest {
 static Path pasta;
 static {try {
  pasta=Files.createTempDirectory("fluxo-proprietario-");
  var mapper=new ObjectMapper();
  var dono=new Contas.Conta("11111111-1111-1111-1111-111111111111","Pessoa Proprietaria","dono@teste.com","hash-nao-usado-neste-teste",false,"NOVO");
  Files.writeString(pasta.resolve("contas.json"),mapper.writeValueAsString(List.of(dono)));
 }catch(Exception e){throw new RuntimeException(e);}}
 @DynamicPropertySource static void props(DynamicPropertyRegistry p){p.add("app.contas",()->pasta.resolve("contas.json").toString());p.add("app.arquivo",()->pasta.resolve("financas.json").toString());}
 @Autowired MockMvc mvc; @Autowired Contas contas; @Autowired BancoDados banco; @Autowired ObjectMapper mapper;
 @MockBean InfinitePayClient client; @MockBean EmailFluxo email;
 @Test void donoPermaneceIsentoClienteExpiraENaoPodeSePromover() throws Exception {
  var dono=contas.buscar("dono@teste.com");var cliente=contas.cadastrar("Pessoa Cliente","cliente@teste.com","senha-segura-123");
  for(var c:List.of(dono,cliente))banco.salvar(c.id(),Map.of("perfil",mapper.writeValueAsString(new Contas.Preferencias(null,Instant.now().minus(Duration.ofDays(40)).toString()))));
  mvc.perform(get("/api/auth/perfil").with(user(dono.email()))).andExpect(jsonPath("$.plano").value("PROPRIETARIO"));
  mvc.perform(get("/api/painel").with(user(dono.email()))).andExpect(status().isOk());
  mvc.perform(get("/api/pagamentos/situacao").with(user(dono.email()))).andExpect(jsonPath("$.isento").value(true)).andExpect(jsonPath("$.disponivel").value(false)).andExpect(jsonPath("$.valorCentavos").value(0));
  mvc.perform(post("/api/pagamentos/checkout").with(user(dono.email())).with(csrf())).andExpect(status().isBadRequest());
  verifyNoInteractions(client);
  mvc.perform(get("/api/painel").with(user(cliente.email()))).andExpect(status().isPaymentRequired());
  mvc.perform(post("/api/auth/cadastro").with(csrf()).contentType("application/json").content("{\"nome\":\"Outra Pessoa\",\"email\":\"outra@teste.com\",\"senha\":\"senha-segura-123\",\"aceitouTermos\":true,\"cienteAssinatura\":true,\"versaoTermos\":\"2026-10-10.1\",\"plano\":\"PROPRIETARIO\"}")).andExpect(status().isBadRequest());
  assertFalse(contas.proprietario(cliente.email()));
 }
 @Test void cadastroSolicitaConfirmacaoQuandoEmailConfigurado() throws Exception {
  when(email.disponivel()).thenReturn(true);
  mvc.perform(post("/api/auth/cadastro").with(csrf()).contentType("application/json").content("{\"nome\":\"Nova Pessoa\",\"email\":\"nova@teste.com\",\"senha\":\"senha-segura-123\",\"aceitouTermos\":true,\"cienteAssinatura\":true,\"versaoTermos\":\"2026-10-10.1\"}")).andExpect(status().isOk());
  verify(email,timeout(3000)).enviar(eq("nova@teste.com"),eq("email"),matches("[A-Za-z0-9_-]{43}"));
  assertFalse(contas.perfil("nova@teste.com").emailConfirmado());
 }
}
