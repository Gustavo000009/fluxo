/* Entradas extras, diário e pagamentos utilizam o mesmo histórico mensal. */
function pago(d) { return (state.painel.despesasPagas || []).includes(d.id); }
function botaoPago(d) {
  return `<button type="button" class="status-pagamento ${pago(d) ? 'pago' : ''}" data-action="pagamento" data-id="${esc(d.id)}" aria-pressed="${pago(d)}" aria-label="${pago(d) ? 'Marcar como pendente' : 'Marcar como pago'}: ${esc(d.nome)}"><span class="bolinha" aria-hidden="true"></span>${pago(d) ? 'Pago' : 'Pendente'}</button>`;
}
function blocoEntradas(p) {
  return `<section class="card entradas-extras"><div class="section-head"><div><span class="eyebrow">ALÉM DO SALÁRIO</span><h2 class="section-title">Entradas extras</h2></div><button class="btn primary" data-action="nova-entrada">+ Adicionar entrada</button></div>
  <p class="muted">Freelas, vendas e outros valores recebidos. A renda total é distribuída pelos percentuais do seu plano.</p>
  <div class="resumo-entradas"><span>Salário <strong>${brl(p.salario)}</strong></span><span>Extras <strong>${brl(p.totalEntradas)}</strong></span><span>Renda total <strong>${brl(p.rendaTotal)}</strong></span></div>
  <div class="lista">${(p.entradas || []).length ? p.entradas.map(e => `<div class="linha-gasto"><div class="info"><strong>${esc(e.nome)}</strong><small>${dataBR(e.data)}</small></div><strong>${brl(e.valor)}</strong><div class="acoes"><button class="icon-btn" data-action="editar-entrada" data-id="${esc(e.id)}" aria-label="Editar ${esc(e.nome)}"><i data-lucide="pencil"></i></button><button class="icon-btn danger" data-action="excluir-entrada" data-id="${esc(e.id)}" aria-label="Excluir ${esc(e.nome)}"><i data-lucide="trash-2"></i></button></div></div>`).join('') : '<p class="vazio">Nenhuma entrada extra neste mês.</p>'}</div></section>`;
}
function modalEntrada(e) {
  abrirModal(`<form class="modal-form" data-form="entrada"><div class="between"><h3>${e ? 'Editar entrada' : 'Nova entrada extra'}</h3><button type="button" class="icon-btn" data-action="fechar" aria-label="Fechar">×</button></div>
  <input type="hidden" name="id" value="${esc(e?.id || '')}"><label>De onde veio?<input name="nome" required maxlength="100" placeholder="Ex.: Freela de design" value="${esc(e?.nome || '')}"></label>
  <div class="row2"><label>Valor recebido<div class="money-input"><span>R$</span><input name="valor" inputmode="decimal" required value="${moedaInput(e?.valor)}"></div></label><label>Data do recebimento<input name="data" type="date" required value="${esc(e?.data || hoje())}"></label></div>
  <div class="actions"><button type="button" class="btn ghost" data-action="fechar">Cancelar</button><button class="btn primary" type="submit">Salvar entrada</button></div></form>`);
}
function blocoPagamentos(p) {
  const lista=p.despesas.filter(d => doMes(d) && grupoChave(d.categoria) !== 'guardar').sort((a,b)=>Number(pago(a))-Number(pago(b)) || a.data.localeCompare(b.data));
  const pagos=lista.filter(pago).reduce((s,d)=>s+d.valor,0), total=lista.reduce((s,d)=>s+d.valor,0);
  return `<section class="card"><div class="section-head"><div><span class="eyebrow">CONTROLE DO MÊS</span><h2 class="section-title">Pagos e pendentes</h2></div><span>${lista.filter(pago).length} de ${lista.length} pagos</span></div>
  <div class="resumo-entradas"><span>Pago <strong>${brl(pagos)}</strong></span><span>A pagar <strong>${brl(total-pagos)}</strong></span></div>
  <p class="muted">Toque na bolinha para confirmar o pagamento. Todos os gastos continuam contando no orçamento.</p>
  <div class="lista pagamentos-lista">${lista.length ? lista.map(d=>`<div class="pagamento-linha"><div><strong>${esc(d.nome)}</strong><small class="muted">${dataBR(d.data)} · ${esc(d.categoria)}</small></div><strong>${brl(d.valor)}</strong>${botaoPago(d)}</div>`).join('') : '<p class="vazio">Registre um gasto ou uma fatura para acompanhar os pagamentos.</p>'}</div></section>`;
}
let diaFiltro = '';
function tabelaDiaria(p) {
  if(diaFiltro && !diaFiltro.startsWith(p.mesAtual)) diaFiltro='';
  const itens=p.despesas.filter(d=>doMes(d) && d.tipo==='VARIAVEL' && !ehFatura(d) && grupoChave(d.categoria)!=='guardar' && (!diaFiltro || d.data===diaFiltro)).sort((a,b)=>b.data.localeCompare(a.data));
  return `<section class="card"><div class="section-head"><div><span class="eyebrow">NO DIA A DIA</span><h2 class="section-title">Gastos diários</h2></div><button class="btn primary" data-action="novo-diario">+ Registrar gasto</button></div>
  <p class="muted">Despesas avulsas pela data informada. Gastos fixos e faturas ficam no painel de pagamentos. Não registre aqui compras já incluídas em uma fatura.</p>
  <div class="filtro-diario"><label>Filtrar por dia<input id="diaGasto" type="date" min="${esc(p.mesAtual)}-01" max="${esc(p.mesAtual)}-${new Date(Number(p.mesAtual.slice(0,4)),Number(p.mesAtual.slice(5)),0).getDate()}" value="${esc(diaFiltro)}"></label><button class="btn ghost" data-action="todos-dias">Todos os dias</button><strong>Total ${diaFiltro ? 'do dia' : 'do mês'}: ${brl(itens.reduce((s,d)=>s+d.valor,0))}</strong></div>
  <div class="tabela-scroll" tabindex="0" role="region" aria-label="Tabela de gastos diários"><table class="tabela-diaria"><thead><tr><th>Data</th><th>Com o que gastou</th><th>Categoria</th><th>Valor</th><th>Pagamento</th><th>Ações</th></tr></thead><tbody>${itens.length ? itens.map(d=>`<tr><td>${dataBR(d.data)}</td><td>${esc(d.nome)}</td><td>${esc(d.categoria)}</td><td>${brl(d.valor)}</td><td>${botaoPago(d)}</td><td><button class="icon-btn" data-action="editar-despesa" data-id="${esc(d.id)}" aria-label="Editar ${esc(d.nome)}"><i data-lucide="pencil"></i></button></td></tr>`).join('') : '<tr><td colspan="6">Nenhum gasto registrado neste período.</td></tr>'}</tbody></table></div></section>`;
}
acoes['nova-entrada']=()=>modalEntrada();
acoes['editar-entrada']=el=>modalEntrada(state.painel.entradas.find(e=>e.id===el.dataset.id));
acoes['excluir-entrada']=async el=>{ if(confirm('Excluir esta entrada extra?')) { setPainel(await api('/api/entradas/'+el.dataset.id,'DELETE'));toast('Entrada excluída.'); } };
acoes['pagamento']=async el=>{
  const d=state.painel.despesas.find(d=>d.id===el.dataset.id);const confirmar=!pago(d);el.disabled=true;
  try {setPainel(await api('/api/despesas/'+d.id+'/pagamento','PUT',{pago:confirmar}));toast(confirmar?'Pagamento confirmado.':'Gasto marcado como pendente.');} finally {el.disabled=false;}
};
acoes['todos-dias']=()=>{diaFiltro='';render();};
acoes['novo-diario']=()=>{modalDespesa('VARIAVEL');if(diaFiltro) document.querySelector('#modal input[name="data"]').value=diaFiltro;};
document.addEventListener('change',e=>{if(e.target.id==='diaGasto'){diaFiltro=e.target.value;render();}});
forms.entrada=async f=>{
  const e=f.elements,id=e.id.value,button=f.querySelector('[type="submit"]');button.disabled=true;
  try {setPainel(await api(id?'/api/entradas/'+id:'/api/entradas',id?'PUT':'POST',{nome:e.nome.value,valor:converterMoedaParaNumero(e.valor.value,0.01),data:e.data.value}));fecharModal();toast('Entrada salva.');}finally{button.disabled=false;}
};
