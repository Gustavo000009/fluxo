// Valores digitados seguem o padrão brasileiro; números da API já são numéricos.
function converterMoedaParaNumero(valor, minimo = 0) {
  const texto = String(valor ?? '').trim().replace(/^R\$\s*/, '');
  if (typeof valor !== 'number' && texto !== '' &&
      !/^(?:\d+|\d{1,3}(?:\.\d{3})+)(?:,\d{1,2})?$/.test(texto)) {
    throw new Error('Informe um valor válido, como 2.500,00 ou 1250,50.');
  }
  const numero = typeof valor === 'number' ? valor : Number(texto.replace(/\./g, '').replace(',', '.'));
  if (!Number.isFinite(numero) || numero < minimo) {
    throw new Error(minimo > 0 ? 'O valor deve ser de pelo menos R$ 0,01.' : 'O valor não pode ser negativo.');
  }
  return numero;
}
const moedaInput = valor => valor == null || valor === '' ? '' :
  new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(valor);
/* ================= Utilidades ================= */
const $ = (s, el = document) => el.querySelector(s);
const brl = n => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(n || 0);
const fmt = n => new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 }).format(n || 0);
const fmtPct = n => new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 }).format(n || 0);
const ajusteTexto = f => !f.ajustePoupanca ? '' : f.ajustePoupanca < 0
  ? `${brl(-f.ajustePoupanca)} destinados aos excessos dos outros blocos. Original: ${brl(f.valorOriginal)} (${fmtPct(f.percentualOriginal)}%).`
  : `${brl(f.ajustePoupanca)} recebidos da poupança para cobrir o excesso. Original: ${brl(f.valorOriginal)} (${fmtPct(f.percentualOriginal)}%).`;
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const LOGO_FLUXO = document.querySelector('.brand-logo').getAttribute('src');
const hoje = () => { const data = new Date().toLocaleDateString('sv-SE'); return state.mes && !data.startsWith(state.mes) ? state.mes + '-01' : data; };
const dataBR = d => d.split('-').reverse().join('/');
const mesExtenso = () => new Date((state.mes || new Date().toLocaleDateString('sv-SE').slice(0,7)) + '-01T12:00:00').toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' });

const state = { painel: null, categorias: [], view: 'dashboard', mes: new Date().toLocaleDateString('sv-SE').slice(0,7), meses: [] };
let charts = {};
const anterior = {}; // valores exibidos antes (para animar os números)

const NAV = [
  { id: 'dashboard', rotulo: 'Início', icone: 'layout-dashboard' },
  { id: 'gastos', rotulo: 'Gastos', icone: 'wallet' },
  { id: 'metas', rotulo: 'Metas', icone: 'target' },
  { id: 'planejamento', rotulo: 'Plano', icone: 'sliders-horizontal' }
];

const GRUPOS = {
  "necessidades": {
    "icone": "🏠",
    "titulo": "Necessidades",
    "subtitulo": "Gastos fixos e essenciais",
    "cor": "#1684a3",
    "desc": "As contas que fazem parte do seu dia a dia.",
    "topicos": [
      [
        "🏠",
        "Aluguel"
      ],
      [
        "💧",
        "Água"
      ],
      [
        "⚡",
        "Energia"
      ],
      [
        "🌐",
        "Internet"
      ],
      [
        "📱",
        "Telefone"
      ],
      [
        "🥗",
        "Alimentação"
      ],
      [
        "🚌",
        "Transporte"
      ],
      [
        "🧾",
        "Outras contas essenciais"
      ]
    ]
  },
  "guardar": {
    "icone": "💰",
    "titulo": "Poupança",
    "subtitulo": "Construa seu futuro",
    "cor": "#16805d",
    "desc": "Esse dinheiro pode ser destinado à sua reserva financeira, investimentos ou objetivos futuros.",
    "topicos": [
      [
        "🛡️",
        "Reserva financeira"
      ],
      [
        "📈",
        "Investimentos"
      ],
      [
        "🎯",
        "Objetivos futuros"
      ]
    ]
  },
  "lazer": {
    "icone": "🎮",
    "titulo": "Lazer",
    "subtitulo": "Aproveite o presente",
    "cor": "#a04d87",
    "desc": "Espaço no orçamento para os momentos que você gosta.",
    "topicos": [
      [
        "🍽️",
        "Restaurantes"
      ],
      [
        "🎬",
        "Cinema"
      ],
      [
        "🎮",
        "Jogos"
      ],
      [
        "✈️",
        "Viagens"
      ],
      [
        "📺",
        "Assinaturas"
      ],
      [
        "🎨",
        "Hobbies"
      ],
      [
        "🎉",
        "Festas"
      ]
    ]
  },
  "emergencia": {
    "icone": "🛡️",
    "titulo": "Emergência",
    "subtitulo": "Prepare-se para imprevistos",
    "cor": "#ab731d",
    "desc": "Valor destinado a situações inesperadas, para ter mais tranquilidade quando precisar.",
    "topicos": [
      [
        "🔧",
        "Manutenção do carro"
      ],
      [
        "🩺",
        "Problemas de saúde"
      ],
      [
        "💼",
        "Perda de renda"
      ],
      [
        "⚠️",
        "Outras despesas imprevistas"
      ]
    ]
  }
};
const ICONE_AVISO = { atencao: '⚠️', boa: '✅', sugestao: '💡', vermelho: '🔴', amarelo: '🟡', verde: '🟢', azul: '🔵' };

function desenharIcones() {
  if (globalThis.lucide) { globalThis.lucide.createIcons(); return; }
  const icones = { 'layout-dashboard': '▦', wallet: '👛', target: '🎯', 'sliders-horizontal': '☷', banknote: '💵', receipt: '🧾', 'piggy-bank': '🐷', plus: '+', pencil: '✎', 'trash-2': '🗑' };
  document.querySelectorAll('i[data-lucide]').forEach(el => {
    el.textContent = icones[el.dataset.lucide] || '•';
    el.style.fontStyle = 'normal';
    el.setAttribute('aria-hidden', 'true');
  });
}

async function api(url, metodo = 'GET', corpo) { return DemoFluxo.request(url, metodo, corpo, state.mes); }

function toast(msg, tipo = 'ok') {
  const t = document.createElement('div');
  t.className = 'toast ' + tipo;
  t.textContent = msg;
  $('#toasts').append(t);
  setTimeout(() => t.classList.add('sai'), 2600);
  setTimeout(() => t.remove(), 3000);
}

const catInfo = nome => state.categorias.find(c => c.nome === nome) || state.categorias[state.categorias.length - 1];
const grupoChave = categoria => ({ NECESSIDADES: 'necessidades', POUPANCA: 'guardar', LAZER: 'lazer', EMERGENCIA: 'emergencia' }[catInfo(categoria)?.grupo] || 'necessidades');
const doMes = d => d.tipo === 'FIXA' || d.data.slice(0, 7) === state.painel.mesAtual;
let blocoAberto = null;

/* ================= Componentes (templates) ================= */
const kpi = (rot, val, key, icone, cor, sub = '') => `
  <div class="card kpi fade">
    <div class="kpi-ico" style="--c:${cor}"><i data-lucide="${icone}"></i></div>
    <span class="muted">${rot}</span>
    <strong class="money" data-money="${val}" data-key="${key}">${brl(val)}</strong>
    ${sub ? `<small class="muted">${sub}</small>` : ''}
  </div>`;

const aviso = a => `
  <div class="aviso ${a.tipo}">
    <span>${ICONE_AVISO[a.tipo] || '💬'}</span>
    <div>${a.titulo ? `<strong>${esc(a.titulo)}</strong>` : ''}<p>${esc(a.texto)}</p></div>
  </div>`;

const barra = (pct, cls = '') => `<div class="bar"><span class="${cls}" style="--w:${Math.min(Math.max(pct, 0), 100)}%"></span></div>`;

function linhaDespesa(d, mesAtual) {
  const c = catInfo(d.categoria);
  const fora = d.tipo === 'VARIAVEL' && d.data.slice(0, 7) !== mesAtual;
  return `
  <div class="linha-gasto">
    <span class="cat-ico" style="--c:${c.cor}">${c.icone}</span>
    <div class="info">
      <strong>${esc(d.nome)}</strong>
      <small class="muted">${esc(c.nome)} · ${d.tipo === 'FIXA' ? 'Vence em ' : ''}${dataBR(d.data)}${fora ? ' · fora do mês atual' : ''}</small>
    </div>
    <strong class="valor">${brl(d.valor)}</strong>
    <div class="acoes">
      <button class="icon-btn" data-action="editar-despesa" data-id="${d.id}" aria-label="Editar"><i data-lucide="pencil"></i></button>
      <button class="icon-btn danger" data-action="excluir-despesa" data-id="${d.id}" aria-label="Excluir"><i data-lucide="trash-2"></i></button>
    </div>
  </div>`;
}

/* ================= Páginas ================= */
const viewOnboarding = () => `
  <section class="card onb fade">
    <div class="logo-big"><img src="${esc(LOGO_FLUXO)}" alt="Fluxo — carteira verde" width="72" height="72"></div>
    <h1>Vamos organizar seu dinheiro?</h1>
    <p class="muted">Informe sua renda mensal para começarmos.</p>
    <form data-form="salario">
      <div class="money-input big"><span>R$</span>
        <input name="salario" type="text" inputmode="decimal" required placeholder="0,00" autofocus></div>
      <button class="btn primary block">Começar</button>
    </form>
  </section>`;

const PREFIXO_FATURA = 'Cartão de crédito · ';
const ehFatura = d => d.categoria === 'Contas' && d.nome.startsWith(PREFIXO_FATURA);
function blocoFaturas(p) {
  const faturas = p.despesas.filter(d => ehFatura(d) && doMes(d));
  return `<section class="credit-bills" aria-labelledby="titulo-faturas">
    <div class="between"><div><span class="eyebrow">CARTÕES DE CRÉDITO</span><h4 id="titulo-faturas">Faturas do mês</h4></div><span class="bill-total">${brl(faturas.reduce((s,d) => s+d.valor,0))}</span></div>
    <p class="muted">Informe o total de cada cartão. Ele entra nos gastos de Necessidades.</p>
    <div class="bill-list">${faturas.length ? faturas.map(d => `<div class="bill-item"><div class="bill-info"><strong>${esc(d.nome.slice(PREFIXO_FATURA.length))}</strong><small>Vencimento ${dataBR(d.data)}</small><b>${brl(d.valor)}</b></div><div class="bill-actions"><button type="button" class="icon-btn" data-action="editar-fatura" data-id="${esc(d.id)}" aria-label="Editar fatura ${esc(d.nome.slice(PREFIXO_FATURA.length))}"><i data-lucide="pencil"></i></button><button type="button" class="icon-btn danger" data-action="excluir-despesa" data-id="${esc(d.id)}" aria-label="Excluir fatura ${esc(d.nome.slice(PREFIXO_FATURA.length))}"><i data-lucide="trash-2"></i></button></div></div>`).join('') : '<p class="bill-empty">Nenhuma fatura registrada neste mês.</p>'}</div>
    <button type="button" class="btn ghost bill-add" data-action="nova-fatura"><i data-lucide="credit-card"></i>Adicionar fatura</button>
    <small class="muted bill-hint">Para não contar duas vezes, não registre também as compras já incluídas na fatura.</small>
  </section>`;
}
function modalFatura(d) {
  abrirModal(`<form class="modal-form" data-form="fatura"><div class="between"><h3>${d ? 'Editar fatura' : 'Nova fatura de cartão'}</h3><button type="button" class="icon-btn" data-action="fechar" aria-label="Fechar">✕</button></div>
    <input type="hidden" name="id" value="${esc(d?.id || '')}">
    <label>Nome do cartão<input name="cartao" required maxlength="40" placeholder="Ex.: Nubank" value="${esc(d ? d.nome.slice(PREFIXO_FATURA.length) : '')}"></label>
    <div class="row2"><label>Valor da fatura<div class="money-input"><span>R$</span><input name="valor" type="text" inputmode="decimal" required placeholder="2.500,00" value="${moedaInput(d?.valor)}"></div></label>
    <label>Vencimento<input name="data" type="date" required value="${esc(d?.data || hoje())}"></label></div>
    <p class="allocation-note">A fatura conta em Necessidades no mês do vencimento. Registre a próxima fatura quando souber o valor.</p>
    <div class="actions"><button type="button" class="btn ghost" data-action="fechar">Cancelar</button><button class="btn primary" type="submit">Salvar fatura</button></div>
  </form>`);
}

function viewDashboard(p) {
  const fatia = f => {
    const g = GRUPOS[f.chave];
    const min = p.rendaTotal * f.faixaMin / 100, max = p.rendaTotal * f.faixaMax / 100;
    const faixa = f.faixaMin === f.faixaMax
      ? `Sugestão: ${f.faixaMin}% (${brl(min)})`
      : `Sugestão: ${f.faixaMin}% a ${f.faixaMax}% (${brl(min)} a ${brl(max)})`;
    const cls = f.gasto > f.valor ? 'red' : f.chave !== 'guardar' && f.uso >= 90 ? 'amber' : '';
    return `
    <article class="card fatia fade" style="--c:${g.cor}" aria-labelledby="grupo-${f.chave}">
      <div class="fatia-top">
        <div class="fatia-heading"><span class="fatia-ico" aria-hidden="true">${g.icone}</span>
          <div><h3 id="grupo-${f.chave}">${g.titulo}</h3><span class="fatia-subtitulo">${g.subtitulo}</span></div>
        </div>
        <span class="tag">${fmtPct(f.percentual)}% <span>da renda</span></span>
      </div>
      <div class="fatia-budget"><span class="eyebrow">${f.ajustePoupanca ? 'Planejado após compensação' : 'Planejado por mês'}</span>
        <strong class="money" data-money="${f.valor}" data-key="f-${f.chave}">${brl(f.valor)}</strong>
        <small class="muted">${faixa}</small>
        ${f.ajustePoupanca ? `<p class="ajuste-note">↔ ${ajusteTexto(f)}</p>` : ''}
      </div>
      <p class="fatia-desc">${g.desc}</p>
      <div class="fatia-details"><h4>O que entra aqui</h4>
        <ul class="topic-list">${g.topicos.map(([icone, texto]) => `<li><span aria-hidden="true">${icone}</span><strong>${esc(texto)}</strong></li>`).join('')}</ul>
      </div>
      <div class="mini"><div class="between small"><span>${f.chave === 'guardar' ? 'Aportes registrados' : 'Gastos registrados'}: <strong>${brl(f.gasto)}</strong></span><span>${f.valor > 0 ? fmt(f.uso) + '% do planejado' : 'Sem verba planejada'}</span></div>
        <div class="bar sm"><span class="${cls}" style="--w:${f.valor > 0 ? Math.min(f.uso, 100) : f.gasto > 0 ? 100 : 0}%"></span></div>
        <p class="saldo-bloco ${f.gasto > f.valor ? 'excedido' : ''}">${f.gasto > f.valor ? 'Acima do planejado: ' + brl(f.gasto - f.valor) : (f.chave === 'guardar' ? 'Falta aportar: ' : 'Ainda reservado neste bloco: ') + brl(f.valor - f.gasto)}</p>
      </div>
      ${f.chave === 'necessidades' ? blocoFaturas(p) : ''}
      <div class="bloco-actions"><button class="btn primary" data-action="registrar-bloco" data-grupo="${f.chave}"><i data-lucide="plus"></i>${f.chave === 'guardar' ? 'Registrar aporte' : 'Registrar valor'}</button><button class="btn ghost" data-action="editar-bloco" data-grupo="${f.chave}">Ver / editar</button></div>
    </article>`;
  };
  const pctGastos = p.rendaTotal ? p.gastos / p.rendaTotal * 100 : 0;
  const clsComp = p.percComprometido > 100 ? 'red' : p.percComprometido > 90 ? 'amber' : '';

  return `
  <section class="hero fade">
    <div><span class="chip">${mesExtenso()}</span>
      <p class="eyebrow hero-eyebrow">SEU DINHEIRO, COM MAIS CLAREZA</p><h1>Cuide do presente.<br><span>Planeje o seu futuro.</span></h1><p class="hero-desc">Cada parte da sua renda com um propósito. Organize suas contas e abra espaço para seus planos.</p></div>
    <form class="card salario-form" data-form="salario">
      <label for="salario">Quanto você recebe por mês?</label>
      <div class="linha">
        <div class="money-input big"><span>R$</span>
          <input id="salario" name="salario" type="text" inputmode="decimal" required value="${moedaInput(p.salario)}"></div>
        <button class="btn primary">Organizar meu salário</button>
      </div>
    </form>
  </section>

  ${blocoEntradas(p)}
  <section class="grid g4">
    ${kpi('Renda total', p.rendaTotal, 'k-salario', 'banknote', '#16a34a')}
    ${kpi('Gastos', p.gastos, 'k-gastos', 'receipt', '#ef4444', `${fmt(pctGastos)}% da renda`)}
    ${kpi('Poupança registrada', p.guardado, 'k-guardado', 'piggy-bank', '#0ea5e9', 'Aportes informados neste mês')}
    ${kpi('Sem destinação', p.disponivel, 'k-disp', 'wallet', '#16805d', '100% da renda já tem destino')}
  </section>

  <section class="card fade">
    <div class="between">
      <div><h3>Quanto você já registrou neste mês</h3><p class="muted">Gastos realizados + aportes informados na poupança</p></div>
      <strong class="big-pct">${fmt(p.percComprometido)}%</strong>
    </div>
    ${barra(p.percComprometido, clsComp)}
    <p class="allocation-note">Toda a renda está planejada. Os <strong>${brl(p.saldoPlanejado)}</strong> ainda não registrados continuam destinados aos blocos abaixo. Excessos são compensados pela poupança ainda não aportada; o que ela não cobrir aparece nos alertas.</p>
  </section>

  ${blocoPagamentos(p)}
  <section>
    <div class="section-head budget-heading"><div><span class="eyebrow">UM PLANO PARA CADA PARTE</span><h2 class="section-title">Sua renda, bem distribuída</h2><p class="muted">Entenda o que entra em cada bloco e quanto separar por mês.</p></div><a class="text-link" href="#planejamento">Ajustar percentuais <span aria-hidden="true">↗</span></a></div>
    <div class="budget-grid">${p.distribuicao.map(fatia).join('')}</div>
  </section>

  <section class="grid g2">
    <div class="card fade">
      <h3 style="margin-bottom:18px">Distribuição da renda</h3>
      <div class="donut"><canvas id="chartDist"></canvas>
        <div class="donut-center"><small class="muted">Renda total</small><strong>${brl(p.rendaTotal)}</strong></div></div>
      <div class="legend">${p.distribuicao.map(f => `<div style="--c:${GRUPOS[f.chave].cor}"><i></i>${GRUPOS[f.chave].titulo} · ${fmtPct(f.percentual)}%</div>`).join('')}</div>
    </div>
    <div class="card fade">
      <h3>Gastos por categoria</h3><p class="category-caption">Valores reais do mês. Aportes da poupança aparecem identificados e não entram no total de gastos.</p>
      ${p.categorias.length
        ? `<div class="chart-box" style="height:${Math.max(180, p.categorias.length * 46)}px"><canvas id="chartCat"></canvas></div>`
        : `<div class="vazio">Use <b>Registrar valor</b> nos blocos para começar.</div>`}
      <div class="category-editor">${p.categorias.map(c => `<button class="category-row" data-action="editar-categoria" data-categoria="${esc(c.categoria)}"><span>${esc(c.icone)} <strong>${esc(c.categoria)}</strong><small>${c.grupo === 'POUPANCA' ? 'Aporte · Poupança' : GRUPOS[grupoChave(c.categoria)].titulo}</small></span><span><strong>${brl(c.valor)}</strong><small>Ver / editar ↗</small></span></button>`).join('')}</div>
    </div>
  </section>

  <section class="card fade">
    <h2 class="section-title" style="margin-bottom:16px">O que eu devo fazer?</h2>
    <div class="avisos">${p.recomendacoes.length ? p.recomendacoes.map(aviso).join('') : '<div class="vazio">Tudo certo por aqui.</div>'}</div>
    ${p.alertas.length ? `<h4 class="sub">Alertas</h4><div class="avisos">${p.alertas.map(aviso).join('')}</div>` : ''}
  </section>`;
}

function viewGastos(p) {
  const fixas = p.despesas.filter(d => d.tipo === 'FIXA' && grupoChave(d.categoria) !== 'guardar').sort((a, b) => a.data.localeCompare(b.data));
  const vars = p.despesas.filter(d => d.tipo === 'VARIAVEL' && grupoChave(d.categoria) !== 'guardar').sort((a, b) => b.data.localeCompare(a.data));
  const aportes = p.despesas.filter(d => grupoChave(d.categoria) === 'guardar').sort((a, b) => b.data.localeCompare(a.data));
  const bloco = (titulo, lista, acao, vazio) => `
    <section class="card fade">
      <div class="section-head"><h3>${titulo}</h3>
        <button class="btn primary" data-action="${acao}"><i data-lucide="plus"></i>Adicionar</button></div>
      <div class="lista">${lista.length ? lista.map(d => linhaDespesa(d, p.mesAtual)).join('') : `<div class="vazio">${vazio}</div>`}</div>
    </section>`;
  return `
  <div class="fade"><h1 class="section-title" style="font-size:1.8rem">Gastos</h1>
    <p class="muted">Gastos variáveis só entram na conta do mês da data informada.</p></div>
  <section class="grid g2">
    <div class="card kpi fade">
      <span class="muted">Total de gastos fixos</span>
      <strong class="money" data-money="${p.gastosFixos}" data-key="g-fixos">${brl(p.gastosFixos)}</strong>
      <small class="muted">${fmt(p.percFixos)}% da sua renda</small>${barra(p.percFixos, p.percFixos > 60 ? 'amber' : '')}
    </div>
    <div class="card kpi fade">
      <span class="muted">Gastos variáveis deste mês</span>
      <strong class="money" data-money="${p.gastosVariaveis}" data-key="g-vars">${brl(p.gastosVariaveis)}</strong>
      <small class="muted">${p.rendaTotal ? fmt(p.gastosVariaveis / p.rendaTotal * 100) : 0}% da sua renda</small>
    </div>
  </section>
  ${tabelaDiaria(p)}
  ${blocoPagamentos(p)}
  ${bloco('Meus gastos fixos', fixas, 'novo-fixo', 'Nenhum gasto fixo ainda. Comece por aluguel, energia, água ou internet.')}
  ${bloco('Gastos variáveis', vars, 'novo-variavel', 'Nenhum gasto variável ainda. Registre mercado, combustível, delivery e outros.')}
  ${bloco('Aportes da poupança', aportes, 'novo-aporte', 'Nenhum aporte registrado. O valor planejado não conta como dinheiro já guardado.')}`;
}

function viewMetas(p) {
  const card = m => `
    <article class="card meta fade">
      <div class="between"><h4>🎯 ${esc(m.nome)}</h4>
        <div class="acoes">
          <button class="icon-btn" data-action="editar-meta" data-id="${m.id}" aria-label="Editar"><i data-lucide="pencil"></i></button>
          <button class="icon-btn danger" data-action="excluir-meta" data-id="${m.id}" aria-label="Excluir"><i data-lucide="trash-2"></i></button>
        </div></div>
      <div class="nums"><span>${brl(m.jaTenho)}</span><span>${brl(m.valorNecessario)}</span></div>
      ${barra(m.progresso)}
      <small class="muted">${fmt(m.progresso)}% concluído</small>
      <div class="texto">${esc(m.texto)}</div>
    </article>`;
  return `
  <div class="section-head fade"><div><h1 class="section-title" style="font-size:1.8rem">Minhas metas</h1>
    <p class="muted">Defina um objetivo e veja em quanto tempo você chega lá.</p></div>
    <button class="btn primary" data-action="nova-meta"><i data-lucide="plus"></i>Nova meta</button></div>
  ${p.metas.length ? `<div class="grid g2">${p.metas.map(card).join('')}</div>`
    : `<div class="card vazio fade">Você ainda não criou metas. Que tal começar com "Reserva de emergência" ou "Viajar"?</div>`}`;
}

function viewPlanejamento(p) {
  const r = p.reserva;
  const campo = k => `
    <div class="pct-item"><label>${GRUPOS[k].icone} ${GRUPOS[k].titulo}
      <div class="money-input"><input class="pct-input" id="pct-${k}" name="${k}" type="number" min="0" max="100" step="1" value="${p.percentuais[k]}"><span style="margin:0">%</span></div></label>
      <small id="prev-${k}"></small></div>`;
  return `
  <div class="fade"><h1 class="section-title" style="font-size:1.8rem">Planejamento</h1>
    <p class="muted">As porcentagens são só uma sugestão. Ajuste ao seu jeito.</p></div>

  <section class="card fade">
    <h3>Personalize seu plano original</h3>
    <p class="allocation-note">Defina os percentuais de partida. Se os gastos ultrapassarem um bloco, a poupança ainda não aportada cobre o excesso automaticamente. Ao editar ou excluir um gasto, os ajustes são recalculados a partir deste plano.</p>
    <form data-form="percentuais">
      <div class="pct-grid">${['necessidades', 'guardar', 'lazer', 'emergencia'].map(campo).join('')}</div>
      <div class="soma" id="somaBox"><span>Total: <span id="soma">0%</span></span>
        <span class="aviso-soma" id="avisoSoma" hidden></span></div>
      <div class="actions" style="justify-content:flex-start">
        <button class="btn primary" id="btnPct">Salvar organização</button>
        <button type="button" class="btn ghost" data-action="padrao-pct">Usar sugestão (55/20/15/10)</button>
      </div>
    </form>
    <div class="ajustes-planejamento"><h4>Distribuição atual deste mês</h4><div class="category-editor">${p.distribuicao.map(f => `<div class="category-row"><span><strong>${GRUPOS[f.chave].icone} ${GRUPOS[f.chave].titulo}</strong><small>Original: ${fmtPct(p.percentuais[f.chave])}%${f.ajustePoupanca ? ' · Ajuste automático aplicado' : ''}</small></span><span><strong>${brl(f.valor)}</strong><small>${fmtPct(f.percentual)}% da renda</small></span></div>`).join('')}</div></div>
  </section>

  <section class="card fade">
    <h3>🛡️ Reserva de emergência</h3>
    <p class="muted" style="margin:6px 0 18px">Uma reserva de emergência serve para lidar com imprevistos sem precisar recorrer a empréstimos ou cartão de crédito.</p>
    <form data-form="essenciais">
      <label>Quanto você gasta por mês com despesas essenciais?
        <div class="money-input"><span>R$</span>
          <input name="valor" type="text" inputmode="decimal" value="${moedaInput(p.essenciaManual)}" placeholder="${moedaInput(r.essenciais)}"></div></label>
      <div class="actions" style="justify-content:flex-start;margin-top:14px"><button class="btn primary">Calcular reserva</button></div>
    </form>
    ${r.automatico ? `<small class="muted" style="display:block;margin-top:10px">Usando automaticamente seus gastos essenciais cadastrados (${brl(r.essenciais)}).</small>` : ''}
    <div class="reserva-cards">
      <div><span>Reserva de 3 meses</span><strong class="money" data-money="${r.tresMeses}" data-key="r3">${brl(r.tresMeses)}</strong></div>
      <div><span>Reserva de 6 meses</span><strong class="money" data-money="${r.seisMeses}" data-key="r6">${brl(r.seisMeses)}</strong></div>
    </div>
  </section>`;
}

const VIEWS = { dashboard: viewDashboard, gastos: viewGastos, metas: viewMetas, planejamento: viewPlanejamento };

/* ================= Renderização ================= */
function renderNav() {
  const html = NAV.map(n => `<a class="nav-link ${n.id === state.view ? 'active' : ''}" href="#${n.id}"><i data-lucide="${n.icone}"></i>${n.rotulo}</a>`).join('');
  const historico = `<button class="nav-link nav-historico" data-action="abrir-historico"><i data-lucide="calendar-days"></i><span>Seu histórico mensal</span></button>`;
  $('#navSide').innerHTML = html + historico;
  $('#navBottom').innerHTML = html + historico;
}

function atualizarTemaGraficos() {
  const estilo = getComputedStyle(document.documentElement);
  const texto = estilo.getPropertyValue('--muted').trim();
  const linha = estilo.getPropertyValue('--line').trim();
  const escuro = document.documentElement.dataset.theme === 'dark';
  Object.values(charts).forEach(chart => {
    chart.options.color = texto;
    const tooltip = chart.options.plugins?.tooltip;
    if (tooltip) {
      tooltip.backgroundColor = escuro ? '#e6eef8' : '#203b32';
      tooltip.titleColor = escuro ? '#152132' : '#ffffff';
      tooltip.bodyColor = escuro ? '#152132' : '#ffffff';
    }
    if (chart.config.type === 'bar') {
      ['x', 'y'].forEach(eixo => {
        chart.options.scales[eixo].ticks ||= {};
        chart.options.scales[eixo].ticks.color = texto;
        chart.options.scales[eixo].border = { color: linha };
      });
    }
    chart.update('none');
  });
}
document.addEventListener('fluxo:tema', atualizarTemaGraficos);

function desenharGraficos(p) {
  if (state.view !== 'dashboard' || !$('#chartDist')) return;
  if (typeof Chart === 'undefined') {
    let inicio = 0;
    const cores = p.distribuicao.map(f => {
      const fim = inicio + f.percentual;
      const faixa = `${GRUPOS[f.chave].cor} ${inicio}% ${fim}%`;
      inicio = fim;
      return faixa;
    });
    $('.donut').innerHTML = `<div class="donut-local" style="background:conic-gradient(${cores.join(',')})" role="img" aria-label="Distribuição da renda; percentuais na legenda abaixo"></div><div class="donut-center"><small class="muted">Renda total</small><strong>${brl(p.rendaTotal)}</strong></div>`;
    if ($('.chart-box')) {
      const maior = Math.max(1, ...p.categorias.map(c => c.valor));
      $('.chart-box').style.height = 'auto';
      $('.chart-box').innerHTML = `<div class="categorias-local">${p.categorias.map(c => `<div><div class="between"><span>${esc(c.icone)} ${c.grupo === 'POUPANCA' ? 'Aporte · ' : ''}${esc(c.categoria)}</span><strong>${brl(c.valor)}</strong></div><div class="categoria-trilho"><span style="width:${Math.max(0, c.valor / maior * 100)}%;background:${esc(c.cor)}"></span></div></div>`).join('')}</div>`;
    }
    return;
  }
  const tip = { callbacks: { label: c => ` ${c.label}: ${brl(c.parsed.y ?? c.parsed.x ?? c.parsed)}` } };
  charts.dist = new Chart($('#chartDist'), {
    type: 'doughnut',
    data: {
      labels: p.distribuicao.map(f => GRUPOS[f.chave].titulo),
      datasets: [{ data: p.distribuicao.map(f => f.valor), backgroundColor: p.distribuicao.map(f => GRUPOS[f.chave].cor), borderWidth: 0, hoverOffset: 6 }]
    },
    options: { cutout: '72%', maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: tip } }
  });
  if (p.categorias.length) {
    charts.cat = new Chart($('#chartCat'), {
      type: 'bar',
      data: {
        labels: p.categorias.map(c => `${c.icone} ${c.grupo === 'POUPANCA' ? 'Aporte · ' : ''}${c.categoria}`),
        datasets: [{ data: p.categorias.map(c => c.valor), backgroundColor: p.categorias.map(c => c.cor), borderRadius: 8, barThickness: 20 }]
      },
      options: {
        indexAxis: 'y', maintainAspectRatio: false,
        plugins: { legend: { display: false }, tooltip: tip },
        scales: { x: { grid: { display: false }, ticks: { callback: v => 'R$ ' + v } }, y: { grid: { display: false } } }
      }
    });
  }
  atualizarTemaGraficos();
}

function animarNumeros() {
  document.querySelectorAll('[data-money]').forEach(el => {
    const alvo = Number(el.dataset.money), chave = el.dataset.key;
    const de = anterior[chave] ?? 0;
    anterior[chave] = alvo;
    if (de === alvo) return;
    const ini = performance.now(), dur = 650;
    const passo = t => {
      const k = Math.min((t - ini) / dur, 1), e = 1 - Math.pow(1 - k, 3);
      el.textContent = brl(de + (alvo - de) * e);
      if (k < 1) requestAnimationFrame(passo);
    };
    requestAnimationFrame(passo);
  });
}

function atualizarPercentuais() {
  if (!$('#somaBox')) return;
  let soma = 0;
  ['necessidades', 'guardar', 'lazer', 'emergencia'].forEach(k => {
    const v = Number($('#pct-' + k).value) || 0;
    soma += v;
    $('#prev-' + k).textContent = brl(state.painel.rendaTotal * v / 100);
  });
  const ok = soma === 100, dif = 100 - soma;
  $('#soma').textContent = soma + '%';
  $('#somaBox').classList.toggle('ok', ok);
  $('#avisoSoma').hidden = ok;
  $('#avisoSoma').textContent = `Suas categorias precisam totalizar 100%. ${dif > 0 ? 'Faltam ' + dif : 'Passou ' + (-dif)}%.`;
  $('#btnPct').disabled = !ok;
}

function render() {
  const p = state.painel;
  const mostrarPainel = p.configurado || p.despesas.length > 0;
  $('#app').classList.toggle('onboarding', !mostrarPainel);
  Object.values(charts).forEach(c => c.destroy());
  charts = {};
  $('#view').innerHTML = periodoCompacto() + (mostrarPainel ? VIEWS[state.view](p) : viewOnboarding());
  renderNav();
  if (mostrarPainel) { desenharGraficos(p); atualizarPercentuais(); }
  desenharIcones();
  animarNumeros();
}

function setPainel(p) {
  state.painel = p;
  if (!state.meses.some(m => m.mes === p.mesAtual)) state.meses.push({mes: p.mesAtual, historicoIncompleto: false});
  render();
}

function seletorMes() {
  const incompleto = state.meses.find(m => m.mes === state.mes)?.historicoIncompleto;
  const opcoes = [...new Set([...state.meses.map(m => m.mes), state.mes])].sort().reverse();
  return `<section class="historico-mensal" aria-label="Histórico mensal">
    <div><strong>Seu histórico mensal</strong><p>Cada mês guarda seu próprio planejamento e seus lançamentos.</p></div>
    <label>Mês selecionado<input id="mesSelecionado" type="month" min="2000-01" max="2100-12" value="${esc(state.mes)}"></label>
    <label>Meses salvos<select id="mesSalvo">${opcoes.map(m => `<option value="${esc(m)}" ${m === state.mes ? 'selected' : ''}>${new Date(m + '-01T12:00:00').toLocaleDateString('pt-BR', {month:'long',year:'numeric'})}</option>`).join('')}</select></label>
    ${incompleto ? '<p class="historico-aviso">Este mês foi recuperado dos registros antigos. A renda, o planejamento e as despesas fixas daquele período não eram guardados; confira e complete os valores.</p>' : ''}
  </section>`;
}
/* ================= Modais ================= */
function abrirModal(html) { const m = $('#modal'); m.innerHTML = html; desenharIcones(); if (!m.open) m.showModal(); }
function fecharModal() { $('#modal').close(); blocoAberto = null; }

function opcoesCategorias(grupo, selecionada) {
  return state.categorias.filter(c => grupoChave(c.nome) === grupo)
    .map(c => `<option value="${esc(c.nome)}" ${c.nome === selecionada ? 'selected' : ''}>${esc(c.icone)} ${esc(c.nome)}</option>`).join('');
}

function modalBloco(grupo, categoria = null) {
  blocoAberto = { grupo, categoria };
  const g = GRUPOS[grupo], f = state.painel.distribuicao.find(x => x.chave === grupo);
  const lista = state.painel.despesas.filter(d => doMes(d) && grupoChave(d.categoria) === grupo && (!categoria || d.categoria === categoria))
    .sort((a, b) => b.data.localeCompare(a.data));
  abrirModal(`<section class="modal-form bloco-modal"><div class="between"><h3>${g.icone} ${esc(categoria || g.titulo)}</h3><button type="button" class="icon-btn" data-action="fechar" aria-label="Fechar">✕</button></div>
    <p class="muted">Lançamentos deste mês. Edite o item para corrigir seu valor; adicione apenas novos gastos ou aportes.</p>
    <div class="bloco-summary"><div><small>Planejado · ${g.titulo}</small><strong>${brl(f.valor)}</strong></div><div><small>${categoria ? 'Registrado nesta categoria' : 'Registrado no bloco'}</small><strong>${brl(lista.reduce((s, d) => s + d.valor, 0))}</strong></div></div>
    ${f.ajustePoupanca ? `<p class="ajuste-note">↔ ${ajusteTexto(f)}</p>` : ''}
    ${grupo === 'guardar' ? '<p class="allocation-note">Aportes não são gastos de consumo. Registrar aqui não altera automaticamente o saldo das metas.</p>' : ''}
    <div class="lista">${lista.length ? lista.map(d => linhaDespesa(d, state.painel.mesAtual)).join('') : '<p class="vazio">Nenhum valor registrado neste mês.</p>'}</div>
    <button class="btn primary" data-action="registrar-bloco" data-grupo="${grupo}" ${categoria ? `data-categoria="${esc(categoria)}"` : ''}><i data-lucide="plus"></i>${grupo === 'guardar' ? 'Registrar aporte' : 'Registrar valor'}</button>
    <a class="text-link" href="#planejamento" data-action="fechar">Ajustar o valor planejado pelos percentuais ↗</a>
  </section>`);
}

function modalDespesa(tipo, d, grupo = 'necessidades', categoria = null) {
  const fixa = tipo === 'FIXA';
  if (d) grupo = grupoChave(d.categoria);
  abrirModal(`
  <form class="modal-form" data-form="despesa">
    <h3>${d ? 'Editar lançamento' : 'Registrar valor'}</h3>
    <input type="hidden" name="id" value="${d ? d.id : ''}">
    <label>Bloco<select name="grupo">${Object.entries(GRUPOS).map(([k,g]) => `<option value="${k}" ${k === grupo ? 'selected' : ''}>${g.icone} ${g.titulo}</option>`).join('')}</select></label>
    <label>Frequência<select name="tipo"><option value="VARIAVEL" ${!fixa ? 'selected' : ''}>Pontual · somente no mês da data</option><option value="FIXA" ${fixa ? 'selected' : ''}>Mensal · repete em todos os meses</option></select></label>
    <p class="allocation-note">Use somente valores fictícios nesta demonstração. Gastos fixos são levados para novos meses. Editar este mês não muda os meses já salvos.</p>
    <label>${fixa ? 'Nome da despesa' : 'Descrição'}
      <input name="nome" required maxlength="60" value="${esc(d ? d.nome : '')}" placeholder="${grupo === 'guardar' ? 'Ex.: Aporte para minha reserva' : grupo === 'emergencia' ? 'Ex.: Conserto do carro' : fixa ? 'Ex.: Aluguel' : 'Ex.: Mercado da semana'}"></label>
    <label>Categoria
      <select name="categoria" required>${opcoesCategorias(grupo, d ? d.categoria : categoria)}</select></label>
    <div class="row2">
      <label>Valor<div class="money-input"><span>R$</span>
        <input name="valor" type="text" inputmode="decimal" required value="${moedaInput(d ? d.valor : null)}"></div></label>
      <label>Data / vencimento
        <input name="data" type="date" required value="${d ? d.data : hoje()}"></label>
    </div>
    <div class="actions"><button type="button" class="btn ghost" data-action="fechar">Cancelar</button><button class="btn primary">Salvar</button></div>
  </form>`);
}

function modalMeta(m) {
  abrirModal(`
  <form class="modal-form" data-form="meta">
    <h3>${m ? 'Editar' : 'Nova'} meta</h3>
    <input type="hidden" name="id" value="${m ? m.id : ''}">
    <label>Nome da meta
      <input name="nome" required maxlength="60" value="${esc(m ? m.nome : '')}" placeholder="Ex.: Comprar notebook"></label>
    <label>Valor necessário<div class="money-input"><span>R$</span>
      <input name="valorNecessario" type="text" inputmode="decimal" required value="${moedaInput(m ? m.valorNecessario : null)}"></div></label>
    <div class="row2">
      <label>Quanto já tenho<div class="money-input"><span>R$</span>
        <input name="jaTenho" type="text" inputmode="decimal" value="${moedaInput(m ? m.jaTenho : 0)}"></div></label>
      <label>Guardar por mês<div class="money-input"><span>R$</span>
        <input name="porMes" type="text" inputmode="decimal" value="${moedaInput(m ? m.porMes : null)}"></div></label>
    </div>
    <div class="actions"><button type="button" class="btn ghost" data-action="fechar">Cancelar</button><button class="btn primary">Salvar</button></div>
  </form>`);
}

/* ================= Eventos ================= */
const forms = {
  async fatura(f) {
    const e=f.elements, id=e.id.value, nome=e.cartao.value.trim();
    if (!nome) throw Error('Informe o nome do cartão.');
    const corpo={tipo:'VARIAVEL',nome:PREFIXO_FATURA+nome,categoria:'Contas',valor:converterMoedaParaNumero(e.valor.value,0.01),data:e.data.value};
    const button=f.querySelector('button[type="submit"]'); button.disabled=true;
    try {
      setPainel(await api(id ? '/api/despesas/'+id : '/api/despesas',id ? 'PUT' : 'POST',corpo));
      fecharModal(); toast(id ? 'Fatura atualizada!' : 'Fatura salva em Necessidades!');
    } finally { button.disabled=false; }
  },
  async salario(f) {
    setPainel(await api('/api/salario', 'PUT', { salario: converterMoedaParaNumero(f.elements.salario.value, 0.01) }));
    toast('Salário salvo! Sua organização foi atualizada.');
  },
  async percentuais(f) {
    const e = f.elements;
    setPainel(await api('/api/percentuais', 'PUT', {
      necessidades: +e.necessidades.value, guardar: +e.guardar.value, lazer: +e.lazer.value, emergencia: +e.emergencia.value
    }));
    toast('Organização atualizada!');
  },
  async essenciais(f) {
    const v = f.elements.valor.value.trim();
    setPainel(await api('/api/essenciais', 'PUT', { valor: v === '' ? null : converterMoedaParaNumero(v) }));
    toast('Reserva calculada!');
  },
  async despesa(f) {
    const e = f.elements, id = e.id.value;
    const corpo = { tipo: e.tipo.value, nome: e.nome.value, categoria: e.categoria.value, valor: converterMoedaParaNumero(e.valor.value, 0.01), data: e.data.value };
    setPainel(id ? await api('/api/despesas/' + id, 'PUT', corpo) : await api('/api/despesas', 'POST', corpo));
    if (blocoAberto) modalBloco(blocoAberto.grupo, blocoAberto.categoria); else fecharModal();
    toast(id ? 'Lançamento atualizado!' : 'Lançamento adicionado!');
  },
  async meta(f) {
    const e = f.elements, id = e.id.value;
    const corpo = { nome: e.nome.value, valorNecessario: converterMoedaParaNumero(e.valorNecessario.value, 0.01), jaTenho: converterMoedaParaNumero(e.jaTenho.value), porMes: converterMoedaParaNumero(e.porMes.value) };
    setPainel(id ? await api('/api/metas/' + id, 'PUT', corpo) : await api('/api/metas', 'POST', corpo));
    fecharModal();
    toast(id ? 'Meta atualizada!' : 'Meta criada!');
  }
};

const acoes = {
  'nova-fatura': () => modalFatura(),
  'editar-fatura': el => modalFatura(state.painel.despesas.find(d => d.id === el.dataset.id)),
  'registrar-bloco': el => { blocoAberto = { grupo: el.dataset.grupo, categoria: el.dataset.categoria || null }; modalDespesa('VARIAVEL', null, el.dataset.grupo, el.dataset.categoria); },
  'editar-bloco': el => modalBloco(el.dataset.grupo),
  'editar-categoria': el => modalBloco(grupoChave(el.dataset.categoria), el.dataset.categoria),
  'novo-aporte': () => modalDespesa('VARIAVEL', null, 'guardar'),
  'novo-fixo': () => modalDespesa('FIXA'),
  'novo-variavel': () => modalDespesa('VARIAVEL'),
  'editar-despesa': el => { const d = state.painel.despesas.find(x => x.id === el.dataset.id); if (ehFatura(d)) modalFatura(d); else modalDespesa(d.tipo, d); },
  'excluir-despesa': async el => {
    if (!confirm('Excluir este lançamento?')) return;
    setPainel(await api('/api/despesas/' + el.dataset.id, 'DELETE'));
    if (blocoAberto) modalBloco(blocoAberto.grupo, blocoAberto.categoria);
    toast('Lançamento excluído.');
  },
  'nova-meta': () => modalMeta(),
  'editar-meta': el => modalMeta(state.painel.metas.find(x => x.id === el.dataset.id)),
  'excluir-meta': async el => {
    if (!confirm('Excluir esta meta?')) return;
    setPainel(await api('/api/metas/' + el.dataset.id, 'DELETE'));
    toast('Meta excluída.');
  },
  'padrao-pct': () => {
    ({ necessidades: 55, guardar: 20, lazer: 15, emergencia: 10 });
    Object.entries({ necessidades: 55, guardar: 20, lazer: 15, emergencia: 10 }).forEach(([k, v]) => { $('#pct-' + k).value = v; });
    atualizarPercentuais();
  },
  fechar: fecharModal
};

document.addEventListener('click', async e => {
  const el = e.target.closest('[data-action]');
  if (!el) return;
  try { await acoes[el.dataset.action]?.(el); } catch (err) { toast(err.message, 'erro'); }
});

document.addEventListener('submit', async e => {
  const f = e.target.closest('form[data-form]');
  if (!f) return;
  e.preventDefault();
  try { await forms[f.dataset.form](f); } catch (err) { toast(err.message, 'erro'); }
});

document.addEventListener('input', e => { if (e.target.matches('.pct-input')) atualizarPercentuais(); });
document.addEventListener('change', e => {
  if (e.target.matches('[data-form="despesa"] [name="grupo"]')) {
    e.target.form.elements.categoria.innerHTML = opcoesCategorias(e.target.value);
  }
});
$('#modal').addEventListener('close', () => { blocoAberto = null; });
$('#modal').addEventListener('click', e => { if (e.target === $('#modal')) fecharModal(); });

function lerRota() {
  const v = location.hash.slice(1);
  state.view = VIEWS[v] ? v : 'dashboard';
  if (state.painel) { render(); window.scrollTo(0, 0); }
}
window.addEventListener('hashchange', lerRota);

/* ================= Início ================= */
async function iniciarFinancas() {
  try {
    const [painel, categorias] = await Promise.all([api('/api/painel'), api('/api/categorias')]);
    state.meses = await api('/api/meses');
    state.mes = painel.mesAtual;
    state.categorias = categorias;
    state.painel = painel;
    const v = location.hash.slice(1);
    state.view = VIEWS[v] ? v : 'dashboard';
    render();
  } catch (err) {
    if (err.codigo === 'TESTE_EXPIRADO') return;
    $('#view').innerHTML = '<div class="card vazio">Não foi possível carregar seus dados. O servidor Spring Boot está rodando?</div>';
  }
}
