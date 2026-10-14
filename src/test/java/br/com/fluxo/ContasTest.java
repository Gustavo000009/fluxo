package br.com.fluxo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import java.nio.file.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc
class ContasTest {
    static Path dir;
    static { try { dir = Files.createTempDirectory("fluxo-tests-"); } catch(Exception e) { throw new RuntimeException(e); } }
    @DynamicPropertySource static void props(DynamicPropertyRegistry p) {
        p.add("app.contas", () -> dir.resolve("contas.json").toString());
        p.add("app.arquivo", () -> dir.resolve("financas.json").toString());
    }
    @Autowired MockMvc mvc;
    @Autowired br.com.fluxo.repository.BancoDados banco;
    void cadastrar(String email) throws Exception {
        mvc.perform(post("/api/auth/cadastro").with(csrf()).contentType("application/json")
            .content("{\"nome\":\"Teste Silva\",\"email\":\""+email+"\",\"senha\":\"senha-segura-123\",\"aceitouTermos\":true,\"cienteAssinatura\":true,\"versaoTermos\":\"2026-10-10.1\"}"))
            .andExpect(status().isOk());
    }
    MockHttpSession login(String email) throws Exception {
        return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).param("email",email).param("password","senha-segura-123"))
            .andExpect(status().isOk()).andExpect(cookie().maxAge("fluxo-login",86400)).andExpect(cookie().httpOnly("fluxo-login",true)).andReturn().getRequest().getSession(false);
    }
    @Test void protegeRotasECsrf() throws Exception {
        mvc.perform(get("/api/painel")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/cadastro").contentType("application/json").content("{}" )).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").with(csrf()).param("email","inexistente@teste.com").param("password","errada")).andExpect(status().isUnauthorized());
    }
    @Test void contasIsoladasESessaoEncerrada() throws Exception {
        cadastrar("primeiro@teste.com"); cadastrar("segundo@teste.com");
        MockHttpSession a=login("primeiro@teste.com"), b=login("segundo@teste.com");
                var legacy = login("primeiro@teste.com");
        legacy.setAttribute("fluxo.expira",System.currentTimeMillis()+3600000L);
        var recovery = mvc.perform(get("/api/auth/perfil").session(legacy)).andExpect(status().isOk())
            .andReturn().getResponse().getCookie("fluxo-login");
        assertNotNull(recovery); assertTrue(recovery.getMaxAge() > 3500 && recovery.getMaxAge() <= 3600);
        legacy.invalidate();
        mvc.perform(get("/api/auth/perfil").cookie(recovery)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/perfil").session(a)).andExpect(jsonPath("$.importarLegado").value(true));
        mvc.perform(post("/api/auth/comecar-novo").session(a).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/auth/perfil").session(login("primeiro@teste.com"))).andExpect(jsonPath("$.importarLegado").value(false));
        var remember = mvc.perform(post("/api/auth/login").with(csrf()).param("email","primeiro@teste.com").param("password","senha-segura-123"))
            .andExpect(status().isOk()).andReturn().getResponse().getCookie("fluxo-login");
        mvc.perform(get("/api/auth/perfil").cookie(remember)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("primeiro@teste.com"));
        var expired = login("primeiro@teste.com"); expired.setAttribute("fluxo.expira", System.currentTimeMillis()-1L);
        mvc.perform(get("/api/auth/perfil").session(expired)).andExpect(status().isUnauthorized()).andExpect(cookie().maxAge("fluxo-login",0));
        mvc.perform(put("/api/salario").session(a).with(csrf()).contentType("application/json").content("{\"salario\":2500}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.salario").value(2500));
        mvc.perform(get("/api/painel").session(b)).andExpect(status().isOk()).andExpect(jsonPath("$.configurado").value(false));
        mvc.perform(put("/api/salario").session(b).with(csrf()).contentType("application/json").content("{\"salario\":4000}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/painel").session(a)).andExpect(jsonPath("$.salario").value(2500));
        mvc.perform(delete("/api/metas/id-da-outra-conta").session(b).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/api/auth/importar").session(b).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/cadastro").with(csrf()).contentType("application/json").content("{\"nome\":\"Teste Silva\",\"email\":\" PRIMEIRO@TESTE.COM \",\"senha\":\"senha-segura-123\",\"aceitouTermos\":true,\"cienteAssinatura\":true,\"versaoTermos\":\"2026-10-10.1\"}"))
            .andExpect(status().isBadRequest());
        String fatura = "{\"tipo\":\"VARIAVEL\",\"nome\":\"Cartão de crédito · Nubank\",\"categoria\":\"Contas\",\"valor\":2500,\"data\":\""+java.time.LocalDate.now()+"\"}";
        var criada = mvc.perform(post("/api/despesas").session(a).with(csrf()).contentType("application/json").content(fatura))
            .andExpect(status().isOk()).andExpect(jsonPath("$.gastos").value(2500))
            .andExpect(jsonPath("$.distribuicao[?(@.chave == 'necessidades')].gasto").value(org.hamcrest.Matchers.contains(2500.0)))
            .andReturn().getResponse().getContentAsString();
        String idFatura = new com.fasterxml.jackson.databind.ObjectMapper().readTree(criada).get("despesas").get(0).get("id").asText();
        mvc.perform(put("/api/despesas/"+idFatura).session(a).with(csrf()).contentType("application/json").content(fatura.replace("2500","1200")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.gastos").value(1200)).andExpect(jsonPath("$.despesas.length()").value(1));
        mvc.perform(get("/api/painel").session(b)).andExpect(jsonPath("$.gastos").value(0));
        mvc.perform(delete("/api/despesas/"+idFatura).session(a).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.gastos").value(0));
        String contas=banco.ler("sistema","contas");
        assertFalse(contas.contains("senha-segura-123")); assertTrue(contas.contains("$2a$12$")); assertTrue(contas.contains("NOVO"));
        // Alterar renda e faturas em outro mês não muda os registros anteriores.
        String anterior=java.time.YearMonth.now().minusMonths(1).toString();
        mvc.perform(put("/api/salario").param("mes",anterior).session(a).with(csrf()).contentType("application/json").content("{\"salario\":1800}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.mesAtual").value(anterior));
        mvc.perform(post("/api/despesas").param("mes",anterior).session(a).with(csrf()).contentType("application/json").content(fatura.replace(java.time.LocalDate.now().toString(),anterior+"-15")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.gastos").value(2500));
        mvc.perform(get("/api/painel").session(a)).andExpect(jsonPath("$.salario").value(2500)).andExpect(jsonPath("$.gastos").value(0));
        mvc.perform(get("/api/painel").param("mes",anterior).session(a)).andExpect(jsonPath("$.salario").value(1800)).andExpect(jsonPath("$.gastos").value(2500));
        mvc.perform(get("/api/painel").param("mes",anterior).session(b)).andExpect(jsonPath("$.salario").value(0)).andExpect(jsonPath("$.gastos").value(0));
        mvc.perform(get("/api/meses").session(a)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/painel").param("mes","../contas").session(a)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/despesas").param("mes",anterior).session(a).with(csrf()).contentType("application/json").content(fatura)).andExpect(status().isBadRequest());
        // Abrir novamente o banco preserva o conteúdo e os hashes das contas.
        var reaberto=new br.com.fluxo.repository.BancoDados(dir.resolve("contas.json").toString());
        assertEquals(contas,reaberto.ler("sistema","contas"));
        mvc.perform(post("/api/auth/logout").session(a).with(csrf())).andExpect(status().isOk()).andExpect(cookie().maxAge("fluxo-login",0));
        assertTrue(a.isInvalid());
        assertTrue(Files.exists(dir.resolve("fluxo-db.mv.db")));
    }
}