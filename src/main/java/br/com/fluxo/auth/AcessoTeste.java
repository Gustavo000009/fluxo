package br.com.fluxo.auth;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
@Configuration
public class AcessoTeste implements WebMvcConfigurer {
    private final Contas contas;
    public AcessoTeste(Contas contas) { this.contas=contas; }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new org.springframework.web.servlet.HandlerInterceptor(){
            @Override public boolean preHandle(jakarta.servlet.http.HttpServletRequest req,jakarta.servlet.http.HttpServletResponse res,Object handler)throws Exception {
                if(req.getUserPrincipal()!=null && contas.congelada(req.getUserPrincipal().getName())){
                    res.setStatus(423);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"erro\":\"Sua conta está congelada. Reative-a para continuar.\",\"codigo\":\"CONTA_CONGELADA\"}");return false;
                }return true;
            }
        }).addPathPatterns("/api/**").excludePathPatterns("/api/auth/perfil","/api/auth/csrf","/api/auth/login","/api/auth/logout","/api/auth/reativar","/api/auth/congelar","/api/auth/conta","/api/auth/recuperar","/api/auth/redefinir","/api/auth/confirmar-email","/api/auth/enviar-confirmacao","/api/pagamentos/infinitepay/webhook/**");
        registry.addInterceptor(new org.springframework.web.servlet.HandlerInterceptor() {
            @Override public boolean preHandle(jakarta.servlet.http.HttpServletRequest req,jakarta.servlet.http.HttpServletResponse res,Object handler) throws Exception {
                if(req.getUserPrincipal()!=null && "EXPIRADO".equals(contas.perfil(req.getUserPrincipal().getName()).plano())) {
                    res.setStatus(402);res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"erro\":\"Seu teste de 7 dias terminou. A assinatura custa R$ 9,90 por mês. Abra Minha conta para consultar os pagamentos. Seus dados foram preservados.\",\"codigo\":\"TESTE_EXPIRADO\"}");return false;
                }
                return true;
            }
        }).addPathPatterns("/api/**").excludePathPatterns("/api/auth/congelar","/api/auth/reativar","/api/auth/conta","/api/pagamentos/**","/api/auth/csrf","/api/auth/cadastro","/api/auth/login","/api/auth/perfil","/api/auth/foto","/api/auth/comecar-novo","/api/auth/logout","/api/auth/recuperar","/api/auth/redefinir","/api/auth/enviar-confirmacao","/api/auth/confirmar-email");
    }
}
