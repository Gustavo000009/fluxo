window.FluxoPagamentos = (() => {
  let retorno = null;
  const parametros = new URLSearchParams(location.search);
  if (parametros.has('order_nsu')) {
    retorno = {pedido:parametros.get('order_nsu'), transacao:parametros.get('transaction_nsu'), slug:parametros.get('slug')};
    // Mantém a confirmação em memória durante o login e remove identificadores da barra de endereço.
    ['order_nsu','transaction_nsu','slug','receipt_url','capture_method'].forEach(k=>parametros.delete(k));
    history.replaceState(null,'',location.pathname+(parametros.size?'?'+parametros:'')+location.hash);
  }
  const data = valor => new Date(valor).toLocaleDateString('pt-BR');
  async function api(url,body) {
    const res=await fetch('/api/pagamentos/'+url,body===undefined?{}:{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
    const json=await res.json();
    if(!res.ok)throw Error(json.erro || 'Não foi possível conferir o pagamento. Tente novamente.');
    return json;
  }
  async function confirmarRetorno() {
    if(!retorno)return;
    await api('confirmar',retorno);
    retorno=null;
  }
  async function montar(el) {
    if(!el)return;
    el.innerHTML='<p role="status">Consultando seu acesso…</p>';
    try {
      const s=await api('situacao');
      if(!el.isConnected)return;
      if(s.isento){el.innerHTML='<h3>Proprietário</h3><strong>Acesso sem assinatura</strong><p>Sua conta tem acesso permanente ao Fluxo. Você não precisa pagar e não está limitada ao teste de 7 dias.</p>';return;}
      const podeRenovar=!s.ativo || new Date(s.acessoAte).getTime()-Date.now()<=7*86400000;
      el.innerHTML=`<h3>${s.ativo?'Seu acesso está ativo':'Continue com o Fluxo'}</h3><p>${s.ativo?'Acesso até '+data(s.acessoAte)+'.':'Seus registros ficam preservados.'}</p><strong>R$ 9,90 por mês</strong><p>${s.disponivel?"Pagamento por Pix ou cartão na InfinitePay. A renovação é feita por você, sem débito automático.":"A assinatura com cobrança automática está em preparação. Nenhuma cobrança será feita sem sua autorização."}</p>${s.disponivel?(podeRenovar?'<button type="button" class="btn primary" data-pagar>Pagar 1 mês · R$ 9,90</button>':'<p class="muted">Você poderá renovar nos últimos 7 dias do seu acesso.</p>'):'<p class="muted">Os pagamentos ainda não estão disponíveis. Estamos preparando a publicação do Fluxo.</p>'}<p class="muted">Ao pagar durante o teste, os dias grátis restantes são mantidos.</p><button type="button" class="btn ghost" data-conferir>Já paguei · conferir acesso</button><p role="status" data-status></p>`;
      const mensagem=el.querySelector('[data-status]');
      const pagar=el.querySelector('[data-pagar]');
      if(pagar)pagar.onclick=async()=>{
        pagar.disabled=true;mensagem.textContent='Abrindo pagamento seguro…';
        try { const pedido=await api('checkout',{});location.assign(pedido.url); }
        catch(e){mensagem.textContent=e.message;pagar.disabled=false;}
      };
      el.querySelector('[data-conferir]').onclick=async e=>{
        const botao=e.currentTarget;botao.disabled=true;mensagem.textContent='Conferindo…';
        try {
          await confirmarRetorno();const atualizado=await api('situacao');
          if(atualizado.ativo)location.reload();
          else mensagem.textContent='Ainda não recebemos a confirmação. Se já pagou, use “Continuar” no checkout ou aguarde e confira novamente. Não é necessário pagar outra vez.';
        }catch(err){mensagem.textContent=err.message;}finally{botao.disabled=false;}
      };
    }catch(e){el.textContent=e.message;}
  }
  return {montar,confirmarRetorno};
})();
