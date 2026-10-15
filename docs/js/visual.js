/* Visual compacto inspirado na referência. Os dados e as ações usam a mesma API. */
function periodoCompacto() {
  const incompleto=state.meses.find(m=>m.mes===state.mes)?.historicoIncompleto;
  return `<div class="periodo-compacto"><button data-action="abrir-historico" class="periodo-botao"><i data-lucide="calendar-days"></i>${mesExtenso()}<i data-lucide="chevron-down"></i></button><span>Seu planejamento</span></div>${incompleto?'<p class="historico-aviso">Histórico recuperado: confira a renda e os gastos fixos deste mês, que não eram guardados no formato antigo.</p>':''}`;
}
function inicioCompacto(p) {
  const saldo=p.rendaTotal-p.gastos-p.guardado;
  const poupanca=p.distribuicao.find(f=>f.chave==='guardar');
  const progresso=poupanca?.valor>0?Math.min(100,p.guardado/poupanca.valor*100):0;
  return `<section class="inicio-titulo"><div><span class="eyebrow">UM DIA DE CADA VEZ</span><h1>Seu dinheiro,<br><span>mais organizado.</span></h1></div><button class="adicionar-redondo" data-action="adicionar-rapido" aria-label="Adicionar registro"><i data-lucide="plus"></i></button></section>
  <section class="resumo-principal card"><div class="between"><span class="muted">Saldo previsto do mês</span><button class="icon-btn" data-action="editar-renda" aria-label="Editar salário"><i data-lucide="pencil"></i></button></div><strong class="saldo-principal ${saldo<0?'negativo':''}">${brl(saldo)}</strong><p class="resumo-legenda">Renda menos gastos e aportes registrados, incluindo contas pendentes.</p>
  <div class="between resumo-meta"><span>Da poupança planejada</span><strong>${fmt(progresso)}%</strong></div>${barra(progresso)}
  <div class="resumo-movimentos"><div><small>↗ ENTRADAS</small><strong class="positivo">${brl(p.rendaTotal)}</strong><span>Salário + entradas extras</span></div><div><small>↘ SAÍDAS</small><strong>${brl(p.gastos)}</strong><span>${brl(p.guardado)} em aportes</span></div></div></section>
  <div class="atalhos-inicio"><button data-action="nova-entrada"><span class="atalho-icone verde"><i data-lucide="arrow-down-left"></i></span>Nova entrada</button><button data-action="novo-variavel"><span class="atalho-icone rosa"><i data-lucide="arrow-up-right"></i></span>Novo gasto</button><button data-action="abrir-calendario"><span class="atalho-icone amarelo"><i data-lucide="calendar-days"></i></span>Calendário</button><button data-action="nova-fatura"><span class="atalho-icone azul"><i data-lucide="credit-card"></i></span>Fatura</button></div>
  <section class="plano-compacto"><div class="section-head"><h2>Seu plano do mês</h2><a class="text-link" href="#planejamento">Ajustar</a></div><div class="plano-cards">${p.distribuicao.map(f=>`<button class="plano-item" data-action="editar-bloco" data-grupo="${f.chave}"><span class="plano-item-top"><span>${GRUPOS[f.chave].icone} ${GRUPOS[f.chave].titulo}</span><small>${fmtPct(f.percentual)}%</small></span><strong>${brl(f.valor)}</strong>${barra(f.uso)}<small>${brl(f.gasto)} ${f.chave==='guardar'?'aportados':'registrados'}</small></button>`).join('')}</div></section>
  ${blocoPagamentos(p)}
  <details class="detalhe-entradas"><summary>Entradas extras <strong>${brl(p.totalEntradas)}</strong><i data-lucide="chevron-down"></i></summary>${blocoEntradas(p)}</details>
  ${p.alertas.length?`<section class="card"><h2>Atenção neste mês</h2><div class="avisos">${p.alertas.map(aviso).join('')}</div></section>`:''}`;
}
let diaCalendario=null;
function calendarioMensal(p) {
  const [ano,mes]=p.mesAtual.split('-').map(Number),inicio=new Date(ano,mes-1,1).getDay(),dias=new Date(ano,mes,0).getDate();
  const contas=p.despesas.filter(d=>grupoChave(d.categoria)!=='guardar');
  const pendentes=contas.filter(d=>!pago(d)),pagas=contas.filter(pago),hojeReal=new Date().toLocaleDateString('sv-SE');
  const soma=lista=>lista.reduce((s,d)=>s+d.valor,0);
  const selecionadas=diaCalendario?contas.filter(d=>d.data===diaCalendario):contas;
  return `<section class="cabecalho-calendario"><div><span class="eyebrow">SUAS CONTAS, DIA A DIA</span><h1>Calendário</h1></div><button class="btn ghost" data-action="abrir-historico">Trocar mês</button></section>
  <section class="card calendario-card"><div class="calendario-totais"><div><span>A pagar</span><strong>${brl(soma(pendentes))}</strong></div><div><span>Em atraso</span><strong class="negativo">${brl(soma(pendentes.filter(d=>d.data<hojeReal)))}</strong></div><div><span>Pago</span><strong class="positivo">${brl(soma(pagas))}</strong></div></div>
  <div class="grade-calendario">${['Dom','Seg','Ter','Qua','Qui','Sex','Sáb'].map(d=>`<span class="nome-dia">${d}</span>`).join('')}${'<span></span>'.repeat(inicio)}${Array.from({length:dias},(_,i)=>{
    const data=p.mesAtual+'-'+String(i+1).padStart(2,'0'),itens=contas.filter(d=>d.data===data);
    return `<button class="dia-calendario ${diaCalendario===data?'selecionado':''} ${data===hojeReal?'hoje':''}" data-action="filtrar-calendario" data-dia="${data}" aria-pressed="${diaCalendario===data}" aria-label="${i+1} de ${mesExtenso()}, ${itens.length} lançamento(s)">${i+1}<span class="pontos-dia">${itens.some(pago)?'<i class="ponto-pago"></i>':''}${itens.some(d=>!pago(d))?'<i class="ponto-pendente"></i>':''}</span></button>`;
  }).join('')}</div><p class="legenda-calendario"><span><i class="ponto-pago"></i>Pago</span><span><i class="ponto-pendente"></i>Pendente</span></p></section>
  <div class="between"><h2>${diaCalendario?'Contas de '+dataBR(diaCalendario):'Contas do mês'}</h2>${diaCalendario?'<button class="btn ghost" data-action="limpar-calendario">Ver mês inteiro</button>':''}</div>${blocoPagamentos({...p,despesas:selecionadas})}`;
}
async function abrirHistorico() {
  abrirModal('<div class="modal-form"><p>Carregando seu histórico…</p><button class="btn" data-action="fechar">Fechar</button></div>');
  try {
    state.meses=await api('/api/meses');
    const meses=[...new Set([...state.meses.map(m=>m.mes),state.mes])].sort().reverse();
    abrirModal(`<div class="modal-form historico-sheet"><div class="between"><div><span class="eyebrow">SEUS REGISTROS</span><h2>Seu histórico mensal</h2></div><button class="icon-btn" data-action="fechar" aria-label="Fechar histórico">×</button></div><p class="muted">Escolha o mês que quer consultar ou organizar.</p><div class="meses-grid">${meses.map(m=>`<button class="mes-opcao ${m===state.mes?'atual':''}" data-action="selecionar-mes" data-mes="${esc(m)}" aria-pressed="${m===state.mes}"><i data-lucide="calendar-days"></i><span>${new Date(m+'-01T12:00:00').toLocaleDateString('pt-BR',{month:'long',year:'numeric'})}<small>${m===state.mes?'Selecionado':'Ver registros'}</small></span></button>`).join('')}</div><form data-form="abrir-mes" class="abrir-mes-form"><label>Escolher outro mês<input name="mes" type="month" min="2000-01" max="2100-12" value="${esc(state.mes)}" required></label><button class="btn primary">Abrir mês</button></form><button class="btn ghost" data-action="abrir-calendario"><i data-lucide="calendar-days"></i>Ver calendário deste mês</button><p class="muted" id="historicoErro" role="alert"></p></div>`);
  } catch(err) {if(err.codigo!=='TESTE_EXPIRADO'){fecharModal();toast(err.message,'erro');}}
}
let mudandoMes=false;
async function selecionarMes(mes) {
  if(mudandoMes)return;
  mudandoMes=true;const controles=[...document.querySelectorAll('#modal button,#modal input')];controles.forEach(el=>el.disabled=true);
  try {
    const painel=await api('/api/painel?mes='+encodeURIComponent(mes));
    const meses=await api('/api/meses');state.mes=mes;state.meses=meses;state.painel=painel;diaCalendario=null;
    fecharModal();render();window.scrollTo(0,0);
  } catch(err){if(err.codigo!=='TESTE_EXPIRADO'){const erro=document.querySelector('#historicoErro');if(erro)erro.textContent=err.message;else toast(err.message,'erro');}}
  finally{mudandoMes=false;controles.forEach(el=>el.disabled=false);}
}
VIEWS.dashboard=inicioCompacto;
VIEWS.calendario=calendarioMensal;
acoes['abrir-historico']=abrirHistorico;
acoes['selecionar-mes']=el=>selecionarMes(el.dataset.mes);
forms['abrir-mes']=f=>selecionarMes(f.elements.mes.value);
acoes['abrir-calendario']=()=>{fecharModal();location.hash='calendario';if(state.view==='calendario')render();};
acoes['filtrar-calendario']=el=>{diaCalendario=el.dataset.dia;render();};
acoes['limpar-calendario']=()=>{diaCalendario=null;render();};
acoes['editar-renda']=()=>abrirModal(`<form class="modal-form" data-form="salario-compacto"><div class="between"><h2>Seu salário</h2><button type="button" class="icon-btn" data-action="fechar" aria-label="Fechar">×</button></div><p class="muted">Valor referente a ${mesExtenso()}.</p><label>Salário mensal<div class="money-input"><span>R$</span><input name="salario" required inputmode="decimal" value="${moedaInput(state.painel.salario)}"></div></label><button class="btn primary">Salvar salário</button></form>`);
forms['salario-compacto']=async f=>{await forms.salario(f);fecharModal();};
acoes['adicionar-rapido']=()=>abrirModal(`<div class="modal-form menu-adicionar"><div class="between"><h2>Adicionar</h2><button class="icon-btn" data-action="fechar" aria-label="Fechar">×</button></div><p class="muted">O que você quer registrar?</p><button data-action="novo-variavel"><span class="atalho-icone rosa"><i data-lucide="plus"></i></span><span><strong>Novo gasto</strong><small>Registrar uma despesa do dia</small></span><i data-lucide="chevron-right"></i></button><button data-action="nova-entrada"><span class="atalho-icone verde"><i data-lucide="arrow-down-left"></i></span><span><strong>Nova entrada</strong><small>Um freela ou outro valor recebido</small></span><i data-lucide="chevron-right"></i></button><button data-action="abrir-calendario"><span class="atalho-icone amarelo"><i data-lucide="check"></i></span><span><strong>Pagar conta</strong><small>Conferir pagamentos do mês</small></span><i data-lucide="chevron-right"></i></button></div>`);
