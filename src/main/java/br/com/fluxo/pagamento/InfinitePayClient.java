package br.com.fluxo.pagamento;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import br.com.fluxo.service.RegraNegocioException;
import java.net.URI;
import java.util.Map;

/** Apenas o servidor conversa com o provedor; nenhum dado de cartão passa pelo Fluxo. */
@Component
public class InfinitePayClient {
    static final String BASE = "https://api.checkout.infinitepay.io";
    final RestTemplate http;
    public InfinitePayClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4000); factory.setReadTimeout(6000);
        http = new RestTemplate(factory);
    }
    public String criar(Map<String,Object> pedido) {
        var resposta = post("/links", pedido);
        String url = resposta.path("url").asText("");
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (!"https".equals(uri.getScheme()) || uri.getUserInfo()!=null || uri.getPort()!=-1 ||
                !("checkout.infinitepay.com.br".equals(host) || "checkout.infinitepay.io".equals(host) || "link.infinitepay.io".equals(host))) throw new IllegalArgumentException();
            return url;
        } catch (IllegalArgumentException e) { throw new RegraNegocioException("A InfinitePay não retornou um endereço de pagamento válido. Tente novamente."); }
    }
    public JsonNode conferir(String handle,String pedido,String transacao,String slug) {
        return post("/payment_check",Map.of("handle",handle,"order_nsu",pedido,"transaction_nsu",transacao,"slug",slug));
    }
    private JsonNode post(String caminho,Map<String,?> corpo) {
        try {
            var resposta = http.postForObject(BASE+caminho,corpo,JsonNode.class);
            if(resposta==null) throw new IllegalStateException();
            return resposta;
        } catch (Exception e) { throw new RegraNegocioException("Não foi possível consultar a InfinitePay. Tente novamente em instantes."); }
    }
}
