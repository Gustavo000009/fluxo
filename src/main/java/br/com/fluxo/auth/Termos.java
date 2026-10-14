package br.com.fluxo.auth;
import br.com.fluxo.service.RegraNegocioException;
import java.time.Instant;
import java.security.MessageDigest;
import java.util.HexFormat;
public final class Termos {
    private Termos() {}
    public static final String VERSAO="2026-10-10.1";
    public static final String DOCUMENTO="/termos/2026-10-10.html";
    public record Aceite(String versao,String documento,String sha256,String aceitoEm,boolean aceitouTermos,boolean cienteAssinatura,int valorMensalCentavos,String moeda,int diasGratis,boolean autorizaDebitoAutomatico) {}
    public static Aceite validar(Boolean aceitou,Boolean ciente,String versao) {
        if(!Boolean.TRUE.equals(aceitou) || !Boolean.TRUE.equals(ciente))throw new RegraNegocioException("Para criar a conta, aceite os Termos de Uso e confirme que o Fluxo é pago por assinatura após os 7 dias grátis.");
        if(!VERSAO.equals(versao))throw new RegraNegocioException("Os termos foram atualizados. Recarregue a página, leia a versão atual e confirme novamente.");
        try(var in=Termos.class.getResourceAsStream("/static"+DOCUMENTO)) {
            if(in==null)throw new IllegalStateException("Documento de termos indisponível.");
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
            return new Aceite(VERSAO,DOCUMENTO,hash,Instant.now().toString(),true,true,990,"BRL",7,false);
        }catch(java.io.IOException|java.security.NoSuchAlgorithmException e){throw new IllegalStateException("Não foi possível registrar os termos.",e);}
    }
}
