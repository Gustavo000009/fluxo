package br.com.fluxo.auth;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Absolute deadline: activity and automatic sign-in never extend the original 24 hours. */
public class LimiteLogin extends OncePerRequestFilter {
    private final Contas contas;
    public LimiteLogin(Contas contas) {this.contas=contas;}
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth.getPrincipal() instanceof String)) {
            var session = req.getSession();
            Long expira = (Long) session.getAttribute("fluxo.expira");
            if (expira == null) {
                expira = session.getCreationTime() + 86400000L;
                if (auth instanceof RememberMeAuthenticationToken && req.getCookies() != null) {
                    // The remember-me filter has already verified this cookie's signature and expiry.
                    for (Cookie cookie : req.getCookies()) if (cookie.getName().equals("fluxo-login")) {
                        try {
                            String token = new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
                            String[] parts = token.split(":");
                            int expiryIndex = parts.length == 4 ? parts.length - 3 : parts.length - 2;
                            expira = Long.parseLong(parts[expiryIndex]);
                        } catch (RuntimeException e) { expira = 0L; }
                    }
                }
                session.setAttribute("fluxo.expira", expira);
            }
            Long autenticadoEm=(Long)session.getAttribute("fluxo.authAt");
            if (System.currentTimeMillis() >= expira || contas.revogadoEm(auth.getName()) > (autenticadoEm==null?session.getCreationTime():autenticadoEm)) {
                session.invalidate(); SecurityContextHolder.clearContext();
                Cookie cookie = new Cookie("fluxo-login", ""); cookie.setPath(req.getContextPath().isEmpty() ? "/" : req.getContextPath());
                cookie.setHttpOnly(true); cookie.setSecure(req.isSecure()); cookie.setMaxAge(0); res.addCookie(cookie);
            }
        }
        chain.doFilter(req,res);
    }
}