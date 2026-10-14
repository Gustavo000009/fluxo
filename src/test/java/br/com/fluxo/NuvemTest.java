package br.com.fluxo;
import br.com.fluxo.repository.BancoDados;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NuvemTest {
    @Test void nuvemNaoCaiSilenciosamenteEmBancoLocal() {
        assertThrows(IllegalArgumentException.class, () -> new BancoDados("dados/contas.json","","","",true));
    }
    @Test void rejeitaBancoRemotoSemValidacaoTls() {
        assertThrows(IllegalArgumentException.class, () -> new BancoDados("dados/contas.json","jdbc:postgresql://example.invalid/fluxo?sslmode=require","user","",true));
    }
}
