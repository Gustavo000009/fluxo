package br.com.fluxo;
import br.com.fluxo.repository.*;
import br.com.fluxo.auth.Contas;
import br.com.fluxo.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import java.nio.file.*;
import java.time.YearMonth;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HistoricoTest {
 @TempDir Path pasta;
 void mes(YearMonth mes) {
   var request=new MockHttpServletRequest();request.setParameter("mes",mes.toString());
   RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
 }
 @Test void migraSemInventarRendaPassadaECarregaFixosNoProximoMes() throws Exception {
   var mapper=new ObjectMapper().findAndRegisterModules();
   var encoder=new BCryptPasswordEncoder(4);
   var conta=new Contas.Conta("conta-a","Pessoa Teste","pessoa@teste.com",encoder.encode("senha-segura-123"),false,"NOVO");
   Path contasPath=pasta.resolve("contas.json");
   mapper.writeValue(contasPath.toFile(),List.of(conta));
   var original=new Dados(); original.salario=new BigDecimal("5000");
   var atual=YearMonth.now(); var passado=atual.minusMonths(1);
   original.despesas.add(new Despesa("f","FIXA","Aluguel","Moradia",new BigDecimal("900"),atual.atDay(10)));
   original.despesas.add(new Despesa("v","VARIAVEL","Cartão de crédito · Nubank","Contas",new BigDecimal("2500"),passado.atDay(15)));
   original.despesas.add(new Despesa("a","VARIAVEL","Mercado","Alimentação",new BigDecimal("100"),atual.atDay(1)));
   Files.createDirectories(pasta.resolve("usuarios"));
   Path arquivo=pasta.resolve("usuarios/conta-a.json");mapper.writeValue(arquivo.toFile(),original);
   byte[] antes=Files.readAllBytes(arquivo);
   var banco=new BancoDados(contasPath.toString());
   try {
     var contas=new Contas(mapper,encoder,contasPath.toString(),banco);
     assertEquals(conta.senhaHash(),contas.buscar(conta.email()).senhaHash());
     assertTrue(encoder.matches("senha-segura-123",contas.buscar(conta.email()).senhaHash()));
     var principal=User.withUsername(conta.email()).password("unused").roles("USER").build();
     SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
     var repo=new ArmazenamentoJson(mapper,pasta.resolve("financas.json").toString(),contas,banco);
     mes(atual);var migrado=repo.carregar();assertEquals(2,migrado.despesas.size());assertEquals(original.salario,migrado.salario);
     mes(passado);var antigo=repo.carregar();assertTrue(antigo.historicoIncompleto);assertEquals(0,antigo.salario.signum());assertEquals("v",antigo.despesas.get(0).id());
     mes(atual.plusMonths(1));var proximo=repo.carregar();assertEquals(1,proximo.despesas.size());assertEquals("FIXA",proximo.despesas.get(0).tipo());assertEquals(original.salario,proximo.salario);
     proximo.salario=new BigDecimal("7000");proximo.despesas.clear();repo.salvar(proximo);
     mes(atual);assertEquals(original.salario,repo.carregar().salario);assertEquals(2,repo.carregar().despesas.size());
     assertArrayEquals(antes,Files.readAllBytes(arquivo));
     // Recriar o repositório não reimporta nem sobrescreve o banco com os arquivos antigos.
     mes(atual.plusMonths(1));var outro=new ArmazenamentoJson(mapper,pasta.resolve("financas.json").toString(),contas,banco);
     assertEquals(new BigDecimal("7000"),outro.carregar().salario);assertEquals(3,outro.meses().size());
     // Uma falha em um lote não grava parcialmente a transação.
     var lote=new LinkedHashMap<String,String>();lote.put("teste-rollback","valor");lote.put("x".repeat(101),"invalido");
     assertThrows(IllegalStateException.class,()->banco.salvar(conta.id(),lote));assertNull(banco.ler(conta.id(),"teste-rollback"));
   } finally { SecurityContextHolder.clearContext();RequestContextHolder.resetRequestAttributes();banco.fechar(); }
 }
}
