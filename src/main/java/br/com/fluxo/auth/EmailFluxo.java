package br.com.fluxo.auth;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import br.com.fluxo.service.RegraNegocioException;
@Service
public class EmailFluxo {
    private final ObjectProvider<JavaMailSender> sender;
    private final String remetente,base;
    public EmailFluxo(ObjectProvider<JavaMailSender> sender,@Value("${app.email.remetente:}") String remetente,@Value("${app.url-publica:}") String base) {this.sender=sender;this.remetente=remetente;this.base=base;}
    public boolean disponivel() {return sender.getIfAvailable()!=null && !remetente.isBlank() && base.startsWith("https://");}
    public void enviar(String destino,String tipo,String token) {
        if(!disponivel()) throw new RegraNegocioException("O envio de e-mails ainda está em configuração. Tente novamente mais tarde.");
        var msg=new SimpleMailMessage();msg.setFrom(remetente);msg.setTo(destino);
        msg.setSubject(tipo.equals("senha")?"Fluxo — redefinir sua senha":"Fluxo — confirmar seu e-mail");
        msg.setText((tipo.equals("senha")?"Você solicitou uma nova senha. O link é válido por 30 minutos.":"Confirme seu endereço de e-mail. O link é válido por 24 horas.")+"\n\n"+base.replaceAll("/$","")+"/#"+(tipo.equals("senha")?"redefinir":"confirmar-email")+"?token="+token+"\n\nSe não foi você, ignore esta mensagem. Nunca compartilhe este link.");
        sender.getObject().send(msg);
    }
}
