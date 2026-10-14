package br.com.fluxo;
import br.com.fluxo.auth.*;
import br.com.fluxo.pagamento.*;
import br.com.fluxo.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
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

@SpringBootTest(properties={"app.infinitepay.habilitado=true","app.url-publica=https://fluxo.example","app.infinitepay.handle=fluxofinancas"})
@AutoConfigureMockMvc
class PagamentosTest {
 static Path pasta;
 static {try{pasta=Files.createTempDirectory("fluxo-pagamentos-");}catch(Exception e){throw new RuntimeException(e);}}
 @DynamicPropertySource static void props(DynamicPropertyRegistry p){p.add("app.contas",()->pasta.resolve("contas.json").toString());p.add("app.arquivo",()->pasta.resolve("financas.json").toString());}
 @Autowired MockMvc mvc; @Autowired Contas contas; @Autowired BancoDados banco; @Autowired ObjectMapper mapper; @Autowired Pagamentos pagamentos;
 @MockBean InfinitePayClient client;
 @BeforeEach void configurar(){when(client.criar(anyMap())).thenReturn("https://checkout.infinitepay.com.br/teste");}
 Contas.Conta conta() {return contas.cadastrar("Pessoa Pagamento",UUID.randomUUID()+"@teste.com","senha-segura-123");}
 String checkout(String email)throws Exception {
  String result=mvc.perform(post("/api/pagamentos/checkout").with(user(email)).with(csrf())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  return mapper.readTree(result).path("pedido").asText();
 }
 String corpo(String id,String transacao)throws Exception {return mapper.writeValueAsString(Map.of("pedido",id,"transacao",transacao,"slug","fatura-teste"));}
 void confirmado()throws Exception{when(client.conferir(anyString(),anyString(),anyString(),anyString())).thenReturn(mapper.readTree("{\"success\":true,\"paid\":true,\"amount\":990,\"paid_amount\":1010}"));}
 @Test void checkoutProtegidoValorFixoEReutilizaPendente()throws Exception {
  var c=conta();
  mvc.perform(post("/api/pagamentos/checkout").with(csrf())).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/pagamentos/checkout").with(user(c.email()))).andExpect(status().isForbidden());
  String id=checkout(c.email());assertEquals(id,checkout(c.email()));
  var captor=org.mockito.ArgumentCaptor.forClass(Map.class);verify(client,times(1)).criar(captor.capture());
  var payload=mapper.valueToTree(captor.getValue());
  assertEquals("fluxofinancas",payload.path("handle").asText());assertEquals(990,payload.path("items").get(0).path("price").asInt());
  assertEquals(id,payload.path("order_nsu").asText());assertTrue(payload.path("webhook_url").asText().startsWith("https://fluxo.example/api/pagamentos/infinitepay/webhook/"+id+"/"));
  assertFalse(payload.has("customer"));
 }
 @Test void confirmaUmaVezPreservaTesteEIsolaUsuarios()throws Exception {
  var c=conta();var outro=conta();String id=checkout(c.email());confirmado();
  mvc.perform(post("/api/pagamentos/confirmar").with(user(outro.email())).with(csrf()).contentType("application/json").content(corpo(id,"transacao-1"))).andExpect(status().isNotFound());
  verify(client,never()).conferir(anyString(),anyString(),anyString(),anyString());
  mvc.perform(post("/api/pagamentos/confirmar").with(user(c.email())).with(csrf()).contentType("application/json").content(corpo(id,"transacao-1"))).andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(true));
  String ate=pagamentos.situacao(c.email()).acessoAte();
  assertEquals(Instant.parse(contas.perfil(c.email()).testeAte()).atZone(ZoneId.of("America/Sao_Paulo")).plusMonths(1).toInstant(),Instant.parse(ate));
  assertEquals("ATIVO",contas.perfil(c.email()).plano());assertEquals("TESTE",contas.perfil(outro.email()).plano());
  mvc.perform(post("/api/pagamentos/confirmar").with(user(c.email())).with(csrf()).contentType("application/json").content(corpo(id,"transacao-1"))).andExpect(status().isOk());
  assertEquals(ate,pagamentos.situacao(c.email()).acessoAte());verify(client,times(1)).conferir("fluxofinancas",id,"transacao-1","fatura-teste");
  mvc.perform(post("/api/pagamentos/checkout").with(user(c.email())).with(csrf())).andExpect(status().isBadRequest());
 }
 @Test void pendenteValorErradoETransacaoRepetidaNaoLiberamAcesso()throws Exception {
  var c=conta();String id=checkout(c.email());
  for(String resposta:List.of("{\"success\":true,\"paid\":false,\"amount\":990}","{\"success\":true,\"paid\":true,\"amount\":1}","{\"success\":false,\"paid\":true,\"amount\":990}")) {
   when(client.conferir(anyString(),anyString(),anyString(),anyString())).thenReturn(mapper.readTree(resposta));
   mvc.perform(post("/api/pagamentos/confirmar").with(user(c.email())).with(csrf()).contentType("application/json").content(corpo(id,"transacao-invalida"))).andExpect(status().isBadRequest());
   assertFalse(pagamentos.situacao(c.email()).ativo());
  }
  confirmado();banco.salvar("pagamentos",Map.of("transacao-"+AcessoEmail.hash("transacao-invalida"),UUID.randomUUID().toString()));
  mvc.perform(post("/api/pagamentos/confirmar").with(user(c.email())).with(csrf()).contentType("application/json").content(corpo(id,"transacao-invalida"))).andExpect(status().isBadRequest());
  assertFalse(pagamentos.situacao(c.email()).ativo());
 }
 @Test void webhookValidaSegredoConsultaProvedorELiberaContaExpirada()throws Exception {
  var c=conta();banco.salvar(c.id(),Map.of("perfil",mapper.writeValueAsString(new Contas.Preferencias(null,Instant.now().minus(Duration.ofDays(10)).toString()))));
  mvc.perform(get("/api/painel").with(user(c.email()))).andExpect(status().isPaymentRequired());
  String id=checkout(c.email());var pedido=mapper.readValue(banco.ler("pagamentos","pedido-"+id),Pagamentos.Pedido.class);
  String webhook="/api/pagamentos/infinitepay/webhook/"+id+"/";
  String body=mapper.writeValueAsString(Map.of("order_nsu",id,"transaction_nsu","transacao-webhook","invoice_slug","fatura-teste","amount",990,"paid",true));
  mvc.perform(post(webhook+"segredo-incorreto").contentType("application/json").content(body)).andExpect(status().isNotFound());
  verify(client,never()).conferir(anyString(),anyString(),anyString(),anyString());
  when(client.conferir(anyString(),anyString(),anyString(),anyString())).thenReturn(mapper.readTree("{\"success\":true,\"paid\":false,\"amount\":990}"));
  mvc.perform(post(webhook+pedido.segredo()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
  assertFalse(pagamentos.situacao(c.email()).ativo());
  confirmado();mvc.perform(post(webhook+pedido.segredo()).contentType("application/json").content(body)).andExpect(status().isOk());
  String ate=pagamentos.situacao(c.email()).acessoAte();
  mvc.perform(post(webhook+pedido.segredo()).contentType("application/json").content(body)).andExpect(status().isOk());assertEquals(ate,pagamentos.situacao(c.email()).acessoAte());
  mvc.perform(get("/api/painel").with(user(c.email()))).andExpect(status().isOk());
  banco.salvar("pagamentos",Map.of("acesso-"+c.id(),Instant.now().minusSeconds(1).toString()));
  mvc.perform(get("/api/painel").with(user(c.email()))).andExpect(status().isPaymentRequired());
 }
 @Test void semPublicacaoNaoCriaCobranca() {
  var c=conta();var local=new Pagamentos(contas,banco,mapper,client,"fluxofinancas","",false);
  assertFalse(local.situacao(c.email()).disponivel());
  assertThrows(br.com.fluxo.service.RegraNegocioException.class,()->local.criar(c.email()));verify(client,never()).criar(anyMap());
 }
}
