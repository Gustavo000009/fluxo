package br.com.fluxo.auth;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
public class Seguranca {
    @org.springframework.beans.factory.annotation.Value("${app.login.chave:}") private String chaveConfigurada="";
    @org.springframework.beans.factory.annotation.Value("${app.database.url:}") private String bancoRemoto="";
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
        private String chaveLogin(String arquivoContas) throws java.io.IOException {
        if(!chaveConfigurada.isBlank()) {
            if(chaveConfigurada.length()<32) throw new IllegalStateException("Chave de login deve ter pelo menos 32 caracteres.");
            return chaveConfigurada;
        }
        if(!bancoRemoto.isBlank()) throw new IllegalStateException("Configure uma chave de login persistente para a nuvem.");
        var path = java.nio.file.Path.of(arquivoContas).toAbsolutePath().resolveSibling("login.key");
        java.nio.file.Files.createDirectories(path.getParent());
        if (!java.nio.file.Files.exists(path)) {
            byte[] bytes = new byte[32]; new java.security.SecureRandom().nextBytes(bytes);
            try { java.nio.file.Files.writeString(path, java.util.Base64.getEncoder().encodeToString(bytes), java.nio.file.StandardOpenOption.CREATE_NEW); }
            catch (java.nio.file.FileAlreadyExistsException ignored) { }
        }
        return java.nio.file.Files.readString(path).trim();
    }
    @Bean org.springframework.security.web.authentication.RememberMeServices loginPersistente(Contas contas, @org.springframework.beans.factory.annotation.Value("${app.contas:dados/contas.json}") String arquivoContas) throws Exception {
        var service = new org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices(chaveLogin(arquivoContas), contas) {
            @Override protected int calculateLoginLifetime(jakarta.servlet.http.HttpServletRequest request, org.springframework.security.core.Authentication authentication) {
                if (request.getRequestURI().endsWith("/api/auth/login")) return 86400;
                var session = request.getSession(false);
                Long expiry = session == null ? null : (Long) session.getAttribute("fluxo.expira");
                if (expiry == null) return 86400;
                return (int) Math.max(1, Math.min(86400, (expiry - System.currentTimeMillis()) / 1000));
            }
        };
        service.setAlwaysRemember(true);
        service.setTokenValiditySeconds(86400);
        service.setCookieName("fluxo-login");
        return service;
    }
    @Bean SecurityFilterChain security(HttpSecurity http, org.springframework.security.web.authentication.RememberMeServices loginPersistente, Contas contas, @org.springframework.beans.factory.annotation.Value("${app.contas:dados/contas.json}") String arquivoContas) throws Exception {
        String chave = chaveLogin(arquivoContas);
        return http.authorizeHttpRequests(a -> a
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/pagamentos/infinitepay/webhook/*/*").permitAll()
                .requestMatchers("/api/auth/csrf", "/api/auth/cadastro", "/api/auth/login", "/api/auth/recuperar", "/api/auth/redefinir", "/api/auth/confirmar-email", "/error").permitAll()
                .requestMatchers("/api/**").authenticated().anyRequest().permitAll())
            .csrf(c -> c.ignoringRequestMatchers(new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/api/pagamentos/infinitepay/webhook/*/*","POST")))
            .requestCache(c -> c.disable())
            .rememberMe(r -> r.key(chave).rememberMeServices(loginPersistente))
            .addFilterBefore(new LimiteLogin(contas), org.springframework.security.web.access.intercept.AuthorizationFilter.class)
            .exceptionHandling(e -> e.authenticationEntryPoint((req,res,ex) -> {
                res.setStatus(401); res.setContentType("application/json"); res.getWriter().write("{\"erro\":\"Entre na sua conta para continuar.\"}");
            }))
            .formLogin(f -> f.loginProcessingUrl("/api/auth/login").usernameParameter("email")
                .successHandler((req,res,a) -> { req.getSession().setAttribute("fluxo.authAt",System.currentTimeMillis()); req.getSession().setAttribute("fluxo.expira", System.currentTimeMillis() + 86400000L); res.setContentType("application/json"); res.getWriter().write("{}"); })
                .failureHandler((req,res,e) -> { res.setStatus(401); res.setContentType("application/json"); res.getWriter().write("{\"erro\":\"E-mail ou senha incorretos.\"}"); }))
            .logout(l -> l.logoutUrl("/api/auth/logout").logoutSuccessHandler((req,res,a) -> { res.setContentType("application/json"); res.getWriter().write("{}"); }))
            .build();
    }
}