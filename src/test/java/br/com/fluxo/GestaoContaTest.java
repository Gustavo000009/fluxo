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
import org.springframework.mock.web.MockHttpSession;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
@SpringBootTest @AutoConfigureMockMvc
class GestaoContaTest {
 static Path pasta;
 static {try{pasta=Files.createTempDirectory("fluxo-gestao-");}catch(Exception e){throw new RuntimeException(e);}}
 @DynamicPropertySource static void props(DynamicPropertyRegistry p){p.add("app.contas",()->pasta.resolve("contas.json").toString());p.add("app.arquivo",()->pasta.resolve("financas.json").toString());}
 @Autowired MockMvc mvc; @Autowired Contas contas; @Autowired BancoDados banco; @Autowired ObjectMapper mapper;
 Contas.Conta nova(){return contas.cadastrar("Pessoa Teste",UUID.randomUUID()+"@teste.com","senha-segura-123");}
 String corpo(String senha,String confirmar)throws Exception{return mapper.writeValueAsString(Map.of("senha",senha,"confirmacao",confirmar));}
 @Test void congelaPreservaDadosBloqueiaApiEReativaSemReiniciarTeste()throws Exception {
  var c=nova();String inicio=contas.perfil(c.email()).testeAte();banco.salvar(c.id(),Map.of("exemplo","dados-preservados"));
  mvc.perform(post("/api/auth/congelar").with(user(c.email())).with(csrf()).contentType("application/json").content(corpo("errada",""))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/auth/congelar").with(user(c.email())).with(csrf()).contentType("application/json").content(corpo("senha-segura-123",""))).andExpect(status().isOk());
  mvc.perform(get("/api/auth/perfil").with(user(c.email()))).andExpect(jsonPath("$.congelada").value(true));
  mvc.perform(get("/api/painel").with(user(c.email()))).andExpect(status().isLocked());
  mvc.perform(post("/api/pagamentos/checkout").with(user(c.email())).with(csrf())).andExpect(status().isLocked());
  assertEquals("dados-preservados",banco.ler(c.id(),"exemplo"));
  mvc.perform(post("/api/auth/reativar").with(user(c.email())).with(csrf()).contentType("application/json").content(corpo("senha-segura-123",""))).andExpect(status().isOk());
  assertFalse(contas.perfil(c.email()).congelada());assertEquals(inicio,contas.perfil(c.email()).testeAte());
  mvc.perform(get("/api/painel").with(user(c.email()))).andExpect(status().isOk());
 }
 @Test void exclusaoExigeSenhaConfirmacaoECsrfERemoveSomenteTitular()throws Exception {
  var c=nova();var outro=nova();banco.salvar(c.id(),Map.of("historico","privado"));banco.salvar(outro.id(),Map.of("historico","outro"));
  for(String[] tentativa:new String[][]{{"errada","EXCLUIR"},{"senha-segura-123",""}})
   mvc.perform(delete("/api/auth/conta").with(user(c.email())).with(csrf()).contentType("application/json").content(corpo(tentativa[0],tentativa[1]))).andExpect(status().isBadRequest());
  mvc.perform(delete("/api/auth/conta").with(user(c.email())).contentType("application/json").content(corpo("senha-segura-123","EXCLUIR"))).andExpect(status().isForbidden());
  var antiga=new MockHttpSession();antiga.setAttribute("fluxo.authAt",System.currentTimeMillis()-10000);
  mvc.perform(delete("/api/auth/conta").with(user(c.email())).with(csrf()).contentType("application/json").content(corpo("senha-segura-123","EXCLUIR"))).andExpect(status().isOk()).andExpect(cookie().maxAge("fluxo-login",0));
  assertThrows(org.springframework.security.core.userdetails.UsernameNotFoundException.class,()->contas.buscar(c.email()));assertTrue(banco.listar(c.id()).isEmpty());assertEquals("outro",banco.ler(outro.id(),"historico"));
  assertThrows(IllegalStateException.class,()->banco.salvar(c.id(),Map.of("historico","recriado")));
  mvc.perform(get("/api/painel").session(antiga).with(user(c.email()))).andExpect(status().isUnauthorized());
  var recriada=contas.cadastrar("Pessoa Teste",c.email(),"senha-segura-123");assertNotEquals(c.id(),recriada.id());assertFalse(recriada.importarLegado());
  var sessaoOutra=new MockHttpSession();sessaoOutra.setAttribute("fluxo.authAt",System.currentTimeMillis()-10000);
  mvc.perform(get("/api/painel").session(sessaoOutra).with(user(c.email()))).andExpect(status().isUnauthorized());
 }
 @Test void emailRepetidoNormalizadoTemMensagemClaraInclusiveCongelado()throws Exception {
  var c=nova();contas.congelar(c.email(),"senha-segura-123",true);
  var dados=Map.of("nome","Outra Pessoa","email","  "+c.email().toUpperCase()+"  ","senha","senha-segura-123","aceitouTermos",true,"cienteAssinatura",true,"versaoTermos",Termos.VERSAO);
  mvc.perform(post("/api/auth/cadastro").with(csrf()).contentType("application/json").content(mapper.writeValueAsString(dados))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").value("Já existe uma conta com este e-mail. Entre na sua conta ou use Esqueci minha senha."));
  assertEquals(c.id(),contas.buscar(c.email()).id());
 }
}
