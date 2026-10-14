package br.com.fluxo.pagamento;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class InfinitePayClientTest {
 @Test void usaContratoDocumentadoSemRedeReal() {
  var client=new InfinitePayClient();var server=MockRestServiceServer.bindTo(client.http).build();
  server.expect(requestTo(InfinitePayClient.BASE+"/links")).andExpect(content().json("{\"handle\":\"fluxofinancas\"}"))
   .andRespond(withSuccess("{\"url\":\"https://checkout.infinitepay.com.br/um-pedido\"}",MediaType.APPLICATION_JSON));
  server.expect(requestTo(InfinitePayClient.BASE+"/payment_check")).andExpect(content().json("{\"handle\":\"fluxofinancas\",\"order_nsu\":\"pedido\",\"transaction_nsu\":\"transacao\",\"slug\":\"fatura\"}"))
   .andRespond(withSuccess("{\"success\":true,\"paid\":true,\"amount\":990}",MediaType.APPLICATION_JSON));
  assertEquals("https://checkout.infinitepay.com.br/um-pedido",client.criar(Map.of("handle","fluxofinancas")));
  assertTrue(client.conferir("fluxofinancas","pedido","transacao","fatura").path("paid").asBoolean());server.verify();
 }
 @Test void rejeitaRedirecionamentoParaOutroSite() {
  var client=new InfinitePayClient();var server=MockRestServiceServer.bindTo(client.http).build();
  server.expect(requestTo(InfinitePayClient.BASE+"/links")).andRespond(withSuccess("{\"url\":\"https://checkout.infinitepay.com.br.evil.example/\"}",MediaType.APPLICATION_JSON));
  assertThrows(br.com.fluxo.service.RegraNegocioException.class,()->client.criar(Map.of("handle","fluxofinancas")));server.verify();
 }
}
