window.FluxoConta = (() => {
  const fetchOriginal = window.fetch.bind(window);
  let csrf, perfil, modo = 'login', instalacao;
  const escape = text => String(text ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  async function token() {
    const res = await fetchOriginal('/api/auth/csrf', {cache:'no-store'});
    if (!res.ok) throw Error('Não foi possível conectar ao Fluxo.');
    csrf = await res.json();
  }
  window.fetch = async (url, options = {}) => {
    const destino = new URL(typeof url === 'string' ? url : url.url, location.href);
    if (destino.origin === location.origin && destino.pathname.startsWith('/api/')) {
      options = {...options, cache:'no-store'};
      if (!['GET','HEAD','OPTIONS'].includes((options.method || 'GET').toUpperCase())) {
        if (!csrf) await token();
        const headers = new Headers(options.headers); headers.set(csrf.header, csrf.token); options.headers = headers;
      }
    }
    const res = await fetchOriginal(url, options);
    if (res.status === 401 && perfil && destino.pathname.startsWith('/api/')) { perfil = null; location.reload(); }
    if(res.status===423 && perfil && !perfil.congelada){perfil.congelada=true;location.reload();}
    return res;
  };
  async function request(url, options) {
    const res = await fetch(url, options); const data = await res.json().catch(() => ({}));
    if (!res.ok) throw Error(data.erro || (res.status === 403 ? 'Sua sessão precisa ser atualizada. Recarregue a página.' : 'Não foi possível concluir. Tente novamente.'));
    return data;
  }
  function tela() {
    document.querySelector('#app').classList.add('onboarding');
    document.querySelector('#view').innerHTML = `<section class="auth-layout"><div class="auth-story"><span class="eyebrow">SEU DINHEIRO, COM DIREÇÃO</span><h1>Mais clareza.<br>Mais tranquilidade.</h1><p>Transforme seu salário em um plano que faz sentido para você.</p><div class="auth-demo"><span>Um plano para cada parte da sua vida</span><div class="allocation"><i></i><i></i><i></i><i></i></div><ul><li>Contas do mês</li><li>Objetivos e sonhos</li><li>Reserva para o futuro</li></ul></div><small>Finanças pessoais, no seu ritmo.</small></div><div class="card auth-card"><span class="eyebrow">BEM-VINDO AO FLUXO</span><h2>${modo === 'login' ? 'Seu próximo passo começa aqui' : 'Crie sua conta'}</h2><p class="muted">${modo === 'login' ? 'Entre para acompanhar seu planejamento.' : 'Experimente por 7 dias grátis. Depois, R$ 9,90 por mês. Sem plano gratuito permanente.'}</p><form id="contaForm">${modo === 'cadastro' ? '<label>Nome completo<input name="nome" autocomplete="name" minlength="3" maxlength="80" placeholder="Seu nome e sobrenome" required></label>' : ''}<label>E-mail<input type="email" name="email" autocomplete="username" maxlength="254" required></label><label>Senha<input type="password" name="senha" autocomplete="${modo === 'login' ? 'current-password' : 'new-password'}" ${modo === 'cadastro' ? 'minlength="10"' : ''} required></label>${modo === 'cadastro' ? `<small class="muted">Use pelo menos 10 caracteres.</small><fieldset class="aceite-cadastro"><legend>Termos e assinatura</legend><label class="aceite-linha"><input type="checkbox" name="aceitouTermos" required><span>Li e aceito os <a href="/termos/2026-10-10.html" target="_blank" rel="noopener">Termos de Uso e Privacidade</a>.</span></label><label class="aceite-linha"><input type="checkbox" name="cienteAssinatura" required><span>Estou ciente de que o Fluxo é pago por assinatura de <strong>R$ 9,90 por mês</strong>, após <strong>7 dias grátis</strong>, sem plano gratuito permanente.</span></label><small class="muted">Este aceite não autoriza débito automático. A contratação e a autorização de pagamento ocorrem separadamente.</small></fieldset>` : ''}<p id="contaErro" role="alert"></p><button class="btn primary" type="submit">${modo === 'login' ? 'Entrar na minha conta' : 'Começar teste de 7 dias'}</button></form>${modo === 'login' ? '<button class="auth-switch" id="esqueciSenha">Esqueci minha senha</button>' : ''}<button id="trocarConta" class="auth-switch">${modo === 'login' ? 'Ainda não tem conta? Criar conta' : 'Já tem conta? Entrar'}</button><small class="muted">Seus dados ficam no servidor do Fluxo que você está acessando. <a href="/termos/2026-10-10.html" target="_blank" rel="noopener">Termos de Uso e Privacidade</a></small></div></section>`;
    function sincronizarAceite() {
      const f=document.querySelector('#contaForm');
      if(!f)return;
      const a=f.querySelector('[name=aceitouTermos]'),c=f.querySelector('[name=cienteAssinatura]');
      f.querySelector('button[type=submit]').disabled=!!a && !(a.checked && c.checked);
    }
    document.querySelectorAll('.aceite-cadastro input').forEach(input=>input.addEventListener('change',sincronizarAceite));
    sincronizarAceite();
    const esqueci=document.querySelector('#esqueciSenha');if(esqueci)esqueci.onclick=()=>telaAcesso('recuperar');
    document.querySelector('#trocarConta').onclick = () => { modo = modo === 'login' ? 'cadastro' : 'login'; tela(); };
    document.querySelector('#contaForm').onsubmit = async e => {
      e.preventDefault(); const form = e.currentTarget, button = form.querySelector('button'); button.disabled = true;
      const values = Object.fromEntries(new FormData(form)); values.email = values.email.trim().toLowerCase();
      try {
        if (modo === 'cadastro') {
          if (!values.nome.trim().match(/\S+\s+\S+/)) throw Error('Informe seu nome completo, com nome e sobrenome.');
          values.aceitouTermos=form.querySelector('[name=aceitouTermos]').checked;
          values.cienteAssinatura=form.querySelector('[name=cienteAssinatura]').checked;
          values.versaoTermos='2026-10-10.1';
          if(!values.aceitouTermos || !values.cienteAssinatura)throw Error('Aceite os termos e confirme a assinatura para criar sua conta.');
          await request('/api/auth/cadastro', {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(values)});
          modo = 'login'; tela(); document.querySelector('[name=email]').value = values.email;
          document.querySelector('#contaErro').textContent = 'Conta criada! Entre com sua senha.';
        } else {
          await request('/api/auth/login', {method:'POST',body:new URLSearchParams({email:values.email,password:values.senha})});
          await token(); await entrar();
        }
      } catch(error) { document.querySelector('#contaErro').textContent = error.message; }
      finally { button.disabled = false; sincronizarAceite(); }
    };
  }
  function telaAcesso(tipo,segredo='') {
    document.querySelector('#app').classList.add('onboarding');
    const recuperar=tipo==='recuperar',redefinir=tipo==='redefinir';
    document.querySelector('#view').innerHTML=`<section class="card auth-card"><span class="eyebrow">ACESSO AO FLUXO</span><h2>${recuperar?'Recuperar senha':redefinir?'Escolha uma nova senha':'Confirmar seu e-mail'}</h2><p class="muted">${recuperar?'Informe o e-mail da sua conta para receber um link de recuperação.':redefinir?'Use pelo menos 10 caracteres. As sessões anteriores serão encerradas.':'Confirme para vincular este e-mail à sua conta.'}</p><form id="acessoForm">${recuperar?'<label>E-mail<input name="email" type="email" autocomplete="email" maxlength="254" required></label>':redefinir?'<label>Nova senha<input name="senha" type="password" autocomplete="new-password" minlength="10" required></label><label>Repita a senha<input name="confirmacao" type="password" autocomplete="new-password" minlength="10" required></label>':''}<button class="btn primary">${recuperar?'Enviar link':redefinir?'Salvar nova senha':'Confirmar e-mail'}</button></form><p id="acessoMensagem" role="status"></p><button class="auth-switch" id="voltarLogin">Voltar para entrar</button></section>`;
    document.querySelector('#voltarLogin').onclick=()=>{modo='login';tela();};
    document.querySelector('#acessoForm').onsubmit=async e=>{
      e.preventDefault();const f=e.currentTarget,b=f.querySelector('button');b.disabled=true;
      try {
        const dados=Object.fromEntries(new FormData(f));
        if(redefinir && dados.senha!==dados.confirmacao)throw Error('As senhas precisam ser iguais.');
        const result=await request('/api/auth/'+(recuperar?'recuperar':redefinir?'redefinir':'confirmar-email'),{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(recuperar?{email:dados.email}:{token:segredo,senha:dados.senha})});
        document.querySelector('#acessoMensagem').textContent=result.mensagem;
        if(!recuperar)f.hidden=true;
      }catch(err){document.querySelector('#acessoMensagem').textContent=err.message;}finally{b.disabled=false;}
    };
  }
  async function entrar() {
    await FluxoPagamentos.confirmarRetorno().catch(()=>{});
    perfil = await request('/api/auth/perfil');
    if (perfil.congelada) { await carregar(); return; }
    if (perfil.plano === 'EXPIRADO') { await carregar(); return; }
    if (perfil.importarLegado) {
      document.querySelector('#view').innerHTML = `<section class="card auth-card"><h2>Como você quer começar?</h2><p>Esta é a primeira conta deste servidor. Você pode importar o planejamento antigo ou começar do zero.</p><p class="muted">O arquivo original será preservado. A importação só funciona antes de salvar dados nesta conta.</p><p role="alert" id="importErro"></p><button class="btn primary" id="importar">Importar dados antigos</button><button class="btn" id="novo">Começar do zero</button></section>`;
      document.querySelector('#importar').onclick = async e => { e.target.disabled=true; try { await request('/api/auth/importar',{method:'POST'}); location.reload(); } catch(err) { document.querySelector('#importErro').textContent=err.message; e.target.disabled=false; } };
      document.querySelector('#novo').onclick = async e => {
        e.target.disabled = true;
        try { await request('/api/auth/comecar-novo', {method:'POST'}); perfil.importarLegado=false; await carregar(); }
        catch(err) { document.querySelector('#importErro').textContent=err.message; e.target.disabled=false; }
      };
    } else await carregar();
  }
  async function carregar() {
    if (!document.querySelector('#minhaConta')) {
      const button = document.createElement('button'); button.id='minhaConta'; button.className='btn account-button'; button.textContent=perfil.nome.trim().split(/\s+/)[0]; button.onclick=conta;
      document.querySelector('.app-toolbar').append(button);
    }
    atualizarBotao();
    if (perfil.congelada) telaCongelada(); else if (perfil.plano === 'EXPIRADO') await expirado(); else await iniciarFinancas();
  }
  function atualizarBotao() {
    const button=document.querySelector('#minhaConta');
    if(button) button.innerHTML=`${perfil.foto ? `<img class="avatar-mini" src="${escape(perfil.foto)}" alt="">` : ''}<span>${escape(perfil.nome.trim().split(/\s+/)[0])}</span>`;
  }
  async function expirado() {
    perfil=await request('/api/auth/perfil');
    document.querySelector('#modal').close();
    document.querySelector('#app').classList.add('onboarding');
    document.querySelector('#view').innerHTML=`<section class="card auth-card"><span class="eyebrow">FLUXO</span><h1>Seu período de acesso terminou</h1><p>Continue organizando seu dinheiro por <strong>R$ 9,90 por mês</strong>.</p><div class="plan-card" id="pagamentoExpirado"></div><button class="btn" id="abrirContaExpirada">Minha conta</button></section>`;
    document.querySelector('#abrirContaExpirada').onclick=conta;
    FluxoPagamentos.montar(document.querySelector('#pagamentoExpirado'));
  }
  function telaCongelada() {
    document.querySelector('#modal').close();document.querySelector('#app').classList.add('onboarding');
    document.querySelector('#view').innerHTML='<section class="card auth-card"><h1>Conta congelada</h1><p>Seus registros estão guardados. Reative a conta quando quiser voltar.</p><p class="muted">Congelar não reinicia o teste nem pausa o prazo de acesso contratado. Não cancela cobranças externas.</p><button class="btn primary" id="reativarCongelada">Reativar conta</button><button class="btn" id="excluirCongelada">Excluir conta</button><button class="btn ghost" id="sairCongelada">Sair</button><p id="congeladaErro" role="alert"></p></section>';
    document.querySelector('#reativarCongelada').onclick=()=>gestaoConta('reativar');
    document.querySelector('#excluirCongelada').onclick=()=>gestaoConta('excluir');
    document.querySelector('#sairCongelada').onclick=async()=>{try{await request('/api/auth/logout',{method:'POST'});location.reload();}catch(e){document.querySelector('#congeladaErro').textContent=e.message;}};
  }
  function gestaoConta(acao) {
    const excluir=acao==='excluir',reativar=acao==='reativar',dialog=document.querySelector('#modal');
    dialog.innerHTML=`<form class="card account-panel" id="gestaoContaForm"><h2>${excluir?'Excluir conta definitivamente':reativar?'Reativar sua conta':'Congelar sua conta'}</h2><p>${excluir?'Seu cadastro, foto e histórico financeiro serão removidos da base ativa. Esta ação não pode ser desfeita pelo aplicativo. Registros de aceite e pagamento, arquivos antigos e backups podem permanecer pelos períodos de conservação aplicáveis.':reativar?'Seus registros voltarão a ficar disponíveis, conforme a validade do seu acesso.':'Seus registros serão preservados e o uso financeiro ficará bloqueado até você reativar a conta.'}</p>${!reativar?'<p class="muted">Esta ação não cancela cobranças contratadas fora do Fluxo. Congelar não pausa o teste nem o prazo de acesso já pago.</p>':''}<label>Senha atual<input name="senha" type="password" autocomplete="current-password" required maxlength="200"></label>${excluir?'<label>Digite EXCLUIR para confirmar<input name="confirmacao" autocomplete="off" required pattern="EXCLUIR" placeholder="EXCLUIR"></label>':''}<p id="gestaoErro" role="alert"></p><button class="btn ${excluir?'danger':'primary'}" type="submit">${excluir?'Excluir definitivamente':reativar?'Confirmar reativação':'Confirmar congelamento'}</button><button class="btn ghost" type="button" id="cancelarGestao">Cancelar</button></form>`;
    if(!dialog.open)dialog.showModal();
    document.querySelector('#cancelarGestao').onclick=()=>{dialog.close();if(!perfil.congelada)conta();};
    document.querySelector('#gestaoContaForm').onsubmit=async e=>{
      e.preventDefault();const form=e.currentTarget,button=form.querySelector('[type=submit]');button.disabled=true;
      try{await request('/api/auth/'+(excluir?'conta':acao),{method:excluir?'DELETE':'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(Object.fromEntries(new FormData(form)))});if(excluir)perfil=null;location.reload();}
      catch(err){document.querySelector('#gestaoErro').textContent=err.message;button.disabled=false;}
    };
  }
  function conta() {
    if(perfil.congelada){telaCongelada();return;}
    const dialog = document.querySelector('#modal');
    dialog.innerHTML = `<div class="card account-panel"><div class="between"><h2>Minha conta</h2><button class="btn" id="fecharConta" aria-label="Fechar">×</button></div>
      <div class="foto-perfil">${perfil.foto ? `<img class="avatar-grande" src="${escape(perfil.foto)}" alt="Sua foto de perfil">` : `<span class="avatar-grande avatar-letra" aria-hidden="true">${escape(perfil.nome.charAt(0))}</span>`}<div><label class="btn ghost">Escolher foto<input id="fotoPerfil" type="file" accept="image/jpeg,image/png" class="arquivo-foto"></label>${perfil.foto ? '<button class="btn ghost" id="removerFoto">Remover foto</button>' : ''}<small class="muted">JPG ou PNG, até 2 MB. A foto será recortada ao centro.</small></div></div>
      <div class="email-status"><p>${perfil.emailConfirmado ? '✓ E-mail confirmado' : 'E-mail ainda não confirmado'}</p>${!perfil.emailConfirmado ? '<button class="btn ghost" id="confirmarMeuEmail">Enviar confirmação</button>' : ''}</div><p>${escape(perfil.nome)}<br><span class="muted">${escape(perfil.email)}</span></p><div class="plan-card"><span class="eyebrow">SEU PLANO</span>${perfil.plano==='TESTE'?`<p>Teste grátis: ${perfil.diasRestantes} dia(s) restante(s), até ${new Date(perfil.testeAte).toLocaleString('pt-BR')}. O teste começou na criação da conta; não há cobrança automática durante esses 7 dias.</p>`:''}<div id="pagamentoConta"></div></div>
      ${instalacao ? '<button class="btn primary" id="instalar">Instalar aplicativo</button>' : ''}<a href="/termos/2026-10-10.html" target="_blank" rel="noopener">Termos de Uso e Privacidade</a><section class="gestao-conta"><h3>Gerenciar conta</h3><button type="button" class="btn" id="congelarConta">Congelar conta</button><button type="button" class="btn danger" id="excluirConta">Excluir conta</button></section><button class="btn" id="sairConta">Sair da conta</button><p id="accountError" role="status"></p></div>`;
    if (!dialog.open) dialog.showModal();
    FluxoPagamentos.montar(document.querySelector('#pagamentoConta'));
    document.querySelector('#fecharConta').onclick=()=>dialog.close();
    document.querySelector('#congelarConta').onclick=()=>gestaoConta('congelar');
    document.querySelector('#excluirConta').onclick=()=>gestaoConta('excluir');
    document.querySelector('#sairConta').onclick=async()=>{ try { await request('/api/auth/logout',{method:'POST'}); location.reload(); } catch(e) {document.querySelector('#accountError').textContent=e.message;} };
    const confirmar=document.querySelector('#confirmarMeuEmail');if(confirmar)confirmar.onclick=async()=>{confirmar.disabled=true;try{const result=await request('/api/auth/enviar-confirmacao',{method:'POST'});document.querySelector('#accountError').textContent=result.mensagem;}catch(err){document.querySelector('#accountError').textContent=err.message;}finally{confirmar.disabled=false;}};
    document.querySelector('#fotoPerfil').onchange=async e=>{
      const arquivo=e.target.files[0];if(!arquivo)return;
      const status=document.querySelector('#accountError');
      if(arquivo.size>2*1024*1024){status.textContent='Escolha uma foto de até 2 MB.';return;}
      e.target.disabled=true;status.textContent='Salvando foto…';
      try { const data=new FormData();data.append('foto',arquivo);perfil=await request('/api/auth/foto',{method:'PUT',body:data});atualizarBotao();conta();document.querySelector('#accountError').textContent='Foto salva.'; }
      catch(err){status.textContent=err.message;e.target.disabled=false;}
    };
    const remover=document.querySelector('#removerFoto');if(remover)remover.onclick=async()=>{
      remover.disabled=true;try{perfil=await request('/api/auth/foto',{method:'DELETE'});atualizarBotao();conta();}catch(err){document.querySelector('#accountError').textContent=err.message;remover.disabled=false;}
    };
    const install=document.querySelector('#instalar');if(install)install.onclick=async()=>{await instalacao.prompt();instalacao=null;conta();};
  }
  window.addEventListener('beforeinstallprompt', e=>{e.preventDefault();instalacao=e;});
  async function iniciar() {
    const rotaAcesso=location.hash.match(/^#(redefinir|confirmar-email)\?token=([A-Za-z0-9_-]+)$/);
    if(rotaAcesso){history.replaceState(null,'',location.pathname);try{await token();telaAcesso(rotaAcesso[1],rotaAcesso[2]);}catch(err){document.querySelector('#view').textContent='Não foi possível conectar. Reabra o link em instantes.';}return;}
    if ('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js').catch(()=>{});
    try { await token(); const res=await fetch('/api/auth/perfil'); if(res.status===401) tela(); else if(res.ok) await entrar(); else throw Error('Não foi possível carregar a conta.'); }
    catch(e) { document.querySelector('#view').innerHTML='<div class="card"><h2>Não foi possível conectar</h2><p>Confira se o servidor está funcionando e recarregue a página.</p></div>'; }
  }
  return {iniciar,expirado};
})();