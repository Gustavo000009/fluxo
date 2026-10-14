package br.com.fluxo;
import br.com.fluxo.auth.*;
import br.com.fluxo.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.*;
import java.util.Map;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc
class RecursosTest {
 static Path pasta;
 static {try{pasta=Files.createTempDirectory("fluxo-recursos-");}catch(Exception e){throw new RuntimeException(e);}}
 @DynamicPropertySource static void props(DynamicPropertyRegistry p) {
   p.add("app.contas",()->pasta.resolve("contas.json").toString());p.add("app.arquivo",()->pasta.resolve("financas.json").toString());
 }
 @Autowired MockMvc mvc; @Autowired Contas contas; @Autowired BancoDados banco; @Autowired ObjectMapper mapper;
 @Test void entradasPagamentosEIsolamentoMensal() throws Exception {
   String email="recursos@teste.com",outro="outro@teste.com";contas.cadastrar("Pessoa Teste",email,"senha-segura-123");contas.cadastrar("Outra Pessoa",outro,"senha-segura-123");
   String mes=YearMonth.now().toString();
   mvc.perform(put("/api/salario").with(user(email)).with(csrf()).contentType("application/json").content("{\"salario\":3000}"))
     .andExpect(status().isOk());
   String entrada="{\"nome\":\"Freela de design\",\"valor\":500,\"data\":\""+mes+"-10\"}";
   var res=mvc.perform(post("/api/entradas").with(user(email)).with(csrf()).contentType("application/json").content(entrada))
     .andExpect(status().isOk()).andExpect(jsonPath("$.salario").value(3000)).andExpect(jsonPath("$.rendaTotal").value(3500)).andExpect(jsonPath("$.totalPlanejado").value(3500)).andReturn().getResponse().getContentAsString();
   String id=mapper.readTree(res).get("entradas").get(0).get("id").asText();
   mvc.perform(put("/api/entradas/"+id).with(user(outro)).with(csrf()).contentType("application/json").content(entrada)).andExpect(status().isNotFound());
   mvc.perform(put("/api/entradas/"+id).with(user(email)).with(csrf()).contentType("application/json").content(entrada.replace("500","600")))
     .andExpect(jsonPath("$.rendaTotal").value(3600)).andExpect(jsonPath("$.entradas.length()").value(1));
   mvc.perform(post("/api/entradas").with(user(email)).with(csrf()).contentType("application/json").content(entrada.replace("500","-1"))).andExpect(status().isBadRequest());
   String despesa="{\"tipo\":\"FIXA\",\"nome\":\"Internet\",\"categoria\":\"Contas\",\"valor\":100,\"data\":\""+mes+"-10\"}";
   var gasto=mvc.perform(post("/api/despesas").with(user(email)).with(csrf()).contentType("application/json").content(despesa)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
   String gastoId=mapper.readTree(gasto).get("despesas").get(0).get("id").asText();
   mvc.perform(put("/api/despesas/"+gastoId+"/pagamento").with(user(email)).with(csrf()).contentType("application/json").content("{\"pago\":true}"))
     .andExpect(status().isOk()).andExpect(jsonPath("$.despesasPagas[0]").value(gastoId)).andExpect(jsonPath("$.gastos").value(100));
   mvc.perform(put("/api/despesas/"+gastoId+"/pagamento").with(user(outro)).with(csrf()).contentType("application/json").content("{\"pago\":true}"))
     .andExpect(status().isNotFound());
   mvc.perform(get("/api/painel").param("mes",YearMonth.now().plusMonths(1).toString()).with(user(email)))
     .andExpect(jsonPath("$.despesas.length()").value(1)).andExpect(jsonPath("$.despesasPagas.length()").value(0)).andExpect(jsonPath("$.entradas.length()").value(0)).andExpect(jsonPath("$.rendaTotal").value(3000));
   mvc.perform(get("/api/painel").with(user(email))).andExpect(jsonPath("$.despesasPagas[0]").value(gastoId)).andExpect(jsonPath("$.rendaTotal").value(3600));
   mvc.perform(put("/api/despesas/"+gastoId+"/pagamento").with(user(email)).with(csrf()).contentType("application/json").content("{\"pago\":false}"))
     .andExpect(jsonPath("$.despesasPagas.length()").value(0));
   mvc.perform(delete("/api/entradas/"+id).with(user(email)).with(csrf())).andExpect(jsonPath("$.rendaTotal").value(3000));
 }
 @Test void fotoPrivadaETesteExpiraSemApagarDados() throws Exception {
   String email="foto@teste.com";var conta=contas.cadastrar("Foto Pessoa",email,"senha-segura-123");
   var inicio=contas.perfil(email);assertEquals("TESTE",inicio.plano());assertEquals(7,inicio.diasRestantes());assertEquals("9,90",inicio.precoMensal());
   var imagem=new java.awt.image.BufferedImage(20,30,java.awt.image.BufferedImage.TYPE_INT_RGB);
   var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(imagem,"png",bytes);
   var foto=new MockMultipartFile("foto","foto.png","image/png",bytes.toByteArray());
   mvc.perform(multipart(org.springframework.http.HttpMethod.PUT,"/api/auth/foto").file(foto).with(user(email)).with(csrf()))
     .andExpect(status().isOk()).andExpect(jsonPath("$.foto").value(org.hamcrest.Matchers.startsWith("data:image/jpeg;base64,")));
   assertEquals(inicio.testeAte(),contas.perfil(email).testeAte());
   var invalid=new MockMultipartFile("foto","arquivo.png","image/png","<svg><script>alert(1)</script></svg>".getBytes());
   mvc.perform(multipart(org.springframework.http.HttpMethod.PUT,"/api/auth/foto").file(invalid).with(user(email)).with(csrf())).andExpect(status().isBadRequest());
   var grande=new MockMultipartFile("foto","grande.png","image/png",new byte[2*1024*1024+1]);
   mvc.perform(multipart(org.springframework.http.HttpMethod.PUT,"/api/auth/foto").file(grande).with(user(email)).with(csrf())).andExpect(status().isBadRequest());
   mvc.perform(multipart(org.springframework.http.HttpMethod.PUT,"/api/auth/foto").file(foto).with(user(email))).andExpect(status().isForbidden());
   mvc.perform(put("/api/salario").with(user(email)).with(csrf()).contentType("application/json").content("{\"salario\":2222}")).andExpect(status().isOk());
   String antes=banco.ler(conta.id(),YearMonth.now().toString());
   banco.salvar(conta.id(),Map.of("perfil",mapper.writeValueAsString(new Contas.Preferencias(contas.perfil(email).foto(),Instant.now().minus(Duration.ofDays(7)).toString()))));
   mvc.perform(get("/api/painel").with(user(email))).andExpect(status().isPaymentRequired());
   mvc.perform(put("/api/salario").with(user(email)).with(csrf()).contentType("application/json").content("{\"salario\":9999}")).andExpect(status().isPaymentRequired());
   mvc.perform(post("/api/auth/importar").with(user(email)).with(csrf())).andExpect(status().isPaymentRequired());
   assertEquals(antes,banco.ler(conta.id(),YearMonth.now().toString()));assertEquals("EXPIRADO",contas.perfil(email).plano());
   mvc.perform(delete("/api/auth/foto").with(user(email)).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.foto").doesNotExist());
   assertEquals("EXPIRADO",contas.perfil(email).plano());
 }
}
