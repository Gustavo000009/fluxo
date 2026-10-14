package br.com.fluxo.service;
import java.time.YearMonth;
import org.springframework.web.context.request.*;
public final class MesSelecionado {
    private MesSelecionado() {}
    public static YearMonth atual() {
        var attrs=RequestContextHolder.getRequestAttributes();
        String valor=attrs instanceof ServletRequestAttributes a?a.getRequest().getParameter("mes"):null;
        if(valor==null) return YearMonth.now();
        try {
            if(!valor.matches("[0-9]{4}-[0-9]{2}")) throw new IllegalArgumentException();
            var mes=YearMonth.parse(valor);
            if(mes.getYear()<2000 || mes.getYear()>2100) throw new IllegalArgumentException();
            return mes;
        } catch(Exception e) { throw new RegraNegocioException("Informe um mês válido entre 2000 e 2100."); }
    }
}
