package br.com.fluxo.pagamento;

import br.com.fluxo.auth.Contas;
import br.com.fluxo.auth.AcessoEmail;
import br.com.fluxo.repository.BancoDados;
import br.com.fluxo.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
public class Pagamentos {
    public record Pedido(String id,String dono,String handle,String segredo,String url,String criadoEm,String pagoEm,String acessoAte,String transacao) {}
    public record Situacao(boolean disponivel,String provedor,int valorCentavos,String acessoAte,boolean ativo,String modalidade,boolean isento) {}
    public record Checkout(String pedido,String url) {}
    private final Contas contas; private final BancoDados banco; private final ObjectMapper mapper; private final InfinitePayClient client;
    private final String handle,base; private final boolean habilitado;
    public Pagamentos(Contas contas,BancoDados banco,ObjectMapper mapper,InfinitePayClient client,
        @Value("${app.infinitepay.handle:fluxofinancas}") String handle,
        @Value("${app.url-publica:}") String base,
        @Value("${app.infinitepay.habilitado:false}") boolean habilitado) {
        this.contas=contas;this.banco=banco;this.mapper=mapper;this.client=client;
        this.handle=handle;this.base=base.replaceAll("/+$","");this.habilitado=habilitado;
    }
    private boolean disponivel() {
        try { var uri=URI.create(base); return habilitado && handle.matches("[A-Za-z0-9_-]{1,100}") && "https".equals(uri.getScheme()) && uri.getHost()!=null && !uri.getHost().equals("localhost") && uri.getUserInfo()==null && uri.getRawQuery()==null && uri.getRawFragment()==null && (uri.getPath().isEmpty() || uri.getPath().equals("/")); }
        catch(Exception e){return false;}
    }
    public Situacao situacao(String email) {
        if(contas.proprietario(email))return new Situacao(false,"InfinitePay",0,null,true,"ISENTO",true);
        String ate=banco.ler("pagamentos","acesso-"+contas.buscar(email).id());
        return new Situacao(disponivel(),"InfinitePay",990,ate,ate!=null && Instant.parse(ate).isAfter(Instant.now()),"RENOVACAO_MANUAL",false);
    }
    private String json(Object value) {try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private Pedido buscar(String id) {
        if(id==null || !id.matches("[a-f0-9-]{36}"))throw new NaoEncontradoException("Pagamento não encontrado.");
        String dado=banco.ler("pagamentos","pedido-"+id);
        if(dado==null)throw new NaoEncontradoException("Pagamento não encontrado.");
        try{return mapper.readValue(dado,Pedido.class);}catch(Exception e){throw new IllegalStateException(e);}
    }
    public synchronized Checkout criar(String email) {
        if(contas.proprietario(email))throw new RegraNegocioException("Sua conta de proprietário tem acesso permanente e não precisa de assinatura.");
        if(!disponivel())throw new RegraNegocioException("Os pagamentos estarão disponíveis quando o Fluxo estiver publicado.");
        var conta=contas.buscar(email);
        var atual=situacao(email);
        if(atual.ativo() && Instant.parse(atual.acessoAte()).isAfter(Instant.now().plus(Duration.ofDays(7))))
            throw new RegraNegocioException("Seu acesso já está ativo. A renovação abre nos últimos 7 dias do período pago.");
        String anterior=banco.ler("pagamentos","pendente-"+conta.id());
        Pedido pedido=anterior==null?null:buscar(anterior);
        if(pedido==null || pedido.pagoEm()!=null) {
            pedido=new Pedido(UUID.randomUUID().toString(),conta.id(),handle,UUID.randomUUID().toString(),null,Instant.now().toString(),null,null,null);
            // Salva antes da chamada externa: o pedido existe mesmo se a resposta do provedor se perder.
            banco.salvar("pagamentos",Map.of("pedido-"+pedido.id(),json(pedido),"pendente-"+conta.id(),pedido.id()));
        }
        if(pedido.url()!=null)return new Checkout(pedido.id(),pedido.url());
        var payload=new HashMap<String,Object>();
        payload.put("handle",pedido.handle()); payload.put("order_nsu",pedido.id());
        payload.put("items",List.of(Map.of("quantity",1,"price",990,"description","Fluxo — 1 mês de acesso. Renovação por pagamento, sem débito automático.")));
        payload.put("redirect_url",base+"/");
        payload.put("webhook_url",base+"/api/pagamentos/infinitepay/webhook/"+pedido.id()+"/"+pedido.segredo());
        String url=client.criar(payload);
        pedido=new Pedido(pedido.id(),pedido.dono(),pedido.handle(),pedido.segredo(),url,pedido.criadoEm(),null,null,null);
        banco.salvar("pagamentos",Map.of("pedido-"+pedido.id(),json(pedido)));
        return new Checkout(pedido.id(),url);
    }
    public synchronized Situacao confirmar(String email,String id,String transacao,String slug) {
        var pedido=buscar(id);
        if(!pedido.dono().equals(contas.buscar(email).id()))throw new NaoEncontradoException("Pagamento não encontrado.");
        verificar(pedido,transacao,slug);
        return situacao(email);
    }
    public synchronized void webhook(String id,String segredo,String transacao,String slug,String orderNsu) {
        var pedido=buscar(id);
        if(segredo==null || !MessageDigest.isEqual(pedido.segredo().getBytes(StandardCharsets.UTF_8),segredo.getBytes(StandardCharsets.UTF_8)) || !pedido.id().equals(orderNsu))
            throw new NaoEncontradoException("Pagamento não encontrado.");
        verificar(pedido,transacao,slug);
    }
    private void verificar(Pedido pedido,String transacao,String slug) {
        if(banco.ler("sistema","excluida-"+pedido.dono())!=null)return;
        if(pedido.pagoEm()!=null)return; // Notificações e retornos repetidos não prolongam o acesso.
        if(transacao==null || slug==null || !transacao.matches("[A-Za-z0-9_-]{1,150}") || !slug.matches("[A-Za-z0-9_-]{1,150}"))
            throw new RegraNegocioException("Faltam os dados da confirmação do pagamento.");
        var resposta=client.conferir(pedido.handle(),pedido.id(),transacao,slug);
        if(!resposta.path("success").isBoolean() || !resposta.path("success").booleanValue() ||
           !resposta.path("paid").isBoolean() || !resposta.path("paid").booleanValue() ||
           !resposta.path("amount").isIntegralNumber() || resposta.path("amount").longValue()!=990)
            throw new RegraNegocioException("O pagamento ainda não foi confirmado no valor de R$ 9,90. Tente conferir novamente.");
        if(banco.ler("sistema","excluida-"+pedido.dono())!=null)return;
        String transacaoChave="transacao-"+AcessoEmail.hash(transacao);
        String usado=banco.ler("pagamentos",transacaoChave);
        if(usado!=null && !usado.equals(pedido.id()))throw new RegraNegocioException("Esta transação já foi vinculada a outro pagamento.");
        Instant inicio=Instant.now();
        String atual=banco.ler("pagamentos","acesso-"+pedido.dono());
        if(atual!=null && Instant.parse(atual).isAfter(inicio))inicio=Instant.parse(atual);
        // Preserva os dias grátis restantes sem acoplar o cálculo a um login ativo.
        String prefs=banco.ler(pedido.dono(),"perfil");
        try { if(prefs!=null) { var teste=Instant.parse(mapper.readTree(prefs).path("testeInicio").asText()).plus(Duration.ofDays(7));if(teste.isAfter(inicio))inicio=teste; } }
        catch(Exception e){throw new IllegalStateException("Não foi possível ler o período de teste.",e);}
        String ate=inicio.atZone(ZoneId.of("America/Sao_Paulo")).plusMonths(1).toInstant().toString();
        var pago=new Pedido(pedido.id(),pedido.dono(),pedido.handle(),pedido.segredo(),pedido.url(),pedido.criadoEm(),Instant.now().toString(),ate,transacao);
        banco.salvar("pagamentos",Map.of("pedido-"+pedido.id(),json(pago),"acesso-"+pedido.dono(),ate,transacaoChave,pedido.id()));
    }
}
