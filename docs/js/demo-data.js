const DEMO_CATEGORIAS = [{"nome": "Moradia", "icone": "🏠", "grupo": "NECESSIDADES", "cor": "#0ea5e9"}, {"nome": "Contas", "icone": "💡", "grupo": "NECESSIDADES", "cor": "#f59e0b"}, {"nome": "Alimentação", "icone": "🍔", "grupo": "NECESSIDADES", "cor": "#f97316"}, {"nome": "Transporte", "icone": "🚗", "grupo": "NECESSIDADES", "cor": "#8b5cf6"}, {"nome": "Saúde", "icone": "🩺", "grupo": "NECESSIDADES", "cor": "#ef4444"}, {"nome": "Educação", "icone": "🎓", "grupo": "NECESSIDADES", "cor": "#14b8a6"}, {"nome": "Lazer", "icone": "🎮", "grupo": "LAZER", "cor": "#ec4899"}, {"nome": "Restaurantes", "icone": "🍕", "grupo": "LAZER", "cor": "#e11d48"}, {"nome": "Compras", "icone": "🛍️", "grupo": "LAZER", "cor": "#6366f1"}, {"nome": "Assinaturas", "icone": "📺", "grupo": "LAZER", "cor": "#0891b2"}, {"nome": "Reserva financeira", "icone": "🛡️", "grupo": "POUPANCA", "cor": "#16805d"}, {"nome": "Investimentos", "icone": "📈", "grupo": "POUPANCA", "cor": "#16805d"}, {"nome": "Objetivos futuros", "icone": "🎯", "grupo": "POUPANCA", "cor": "#16805d"}, {"nome": "Manutenção imprevista", "icone": "🔧", "grupo": "EMERGENCIA", "cor": "#ab731d"}, {"nome": "Saúde imprevista", "icone": "🩺", "grupo": "EMERGENCIA", "cor": "#ab731d"}, {"nome": "Perda de renda", "icone": "💼", "grupo": "EMERGENCIA", "cor": "#ab731d"}, {"nome": "Outros imprevistos", "icone": "⚠️", "grupo": "EMERGENCIA", "cor": "#ab731d"}, {"nome": "Outros", "icone": "📦", "grupo": "NECESSIDADES", "cor": "#64748b"}];
// Adapter exclusivo do portfólio: não faz requisições nem persiste dados financeiros.
const DemoFluxo = (() => {
  let meses = {}, seq = 100;
  const keys = ['necessidades','guardar','lazer','emergencia'];
  const groups = ['NECESSIDADES','POUPANCA','LAZER','EMERGENCIA'];
  const sum = a => a.reduce((s,x)=>s+Math.round(x.valor*100),0)/100;
  const cents = n => Math.round(n*100)/100;
  function rate(total, weights) {
    const units=Math.round(total*100), all=weights.reduce((a,b)=>a+b,0);
    if(!all) return weights.map(()=>0);
    const raw=weights.map(w=>units*w/all), parts=raw.map(Math.floor);
    const order=raw.map((v,i)=>({i,r:v-parts[i]})).sort((a,b)=>b.r-a.r);
    for(let n=units-parts.reduce((a,b)=>a+b,0),i=0;i<n;i++) parts[order[i%order.length].i]++;
    return parts.map(v=>v/100);
  }
  function seed(m, empty=false) {
    const d=(id,nome,categoria,valor,dia,tipo='VARIAVEL')=>({id:m+'-'+id,nome,categoria,valor,data:m+'-'+dia,tipo});
    return {salario:empty?0:5000,percentuais:{necessidades:55,guardar:20,lazer:15,emergencia:10},essenciaManual:null,
      despesas:empty?[]:[d('1','Aluguel','Moradia',1400,'05','FIXA'),d('2','Mercado da semana','Alimentação',420,'08'),d('3','Cartão de crédito · Nubank','Contas',580,'15'),d('4','Cinema com amigos','Lazer',90,'10'),d('5','Aporte para minha reserva','Reserva financeira',800,'12'),d('6','Internet','Contas',100,'18','FIXA')],
      entradas:empty?[]:[{id:m+'-e1',nome:'Freela de design',valor:650,data:m+'-10'}],
      despesasPagas:empty?[]:[m+'-1',m+'-2',m+'-4'],
      metas:empty?[]:[{id:m+'-m1',nome:'Reserva de emergência',valorNecessario:15000,jaTenho:4200,porMes:800},{id:m+'-m2',nome:'Próxima viagem',valorNecessario:4000,jaTenho:1250,porMes:250}]};
  }
  function reset() { meses={}; const now=new Date(); for(let i=0;i<3;i++){let date=new Date(now.getFullYear(),now.getMonth()-i,1);let m=date.getFullYear()+'-'+String(date.getMonth()+1).padStart(2,'0');meses[m]=seed(m);meses[m].salario-=i*200;}}
  function calc(m) {
    const d=meses[m] ||= seed(m,true), totalEntradas=sum(d.entradas), rendaTotal=cents(d.salario+totalEntradas);
    const cat=n=>DEMO_CATEGORIAS.find(c=>c.nome===n)||DEMO_CATEGORIAS.at(-1);
    const expenses=d.despesas.filter(x=>x.tipo==='FIXA'||x.data.startsWith(m));
    const spent=groups.map(g=>sum(expenses.filter(x=>cat(x.categoria).grupo===g)));
    const original=rate(rendaTotal,keys.map(k=>d.percentuais[k]));
    const excess=spent.map((n,i)=>i===1?0:Math.max(0,cents(n-original[i])));
    const totalExcess=cents(excess.reduce((a,b)=>a+b,0)), compensado=Math.min(totalExcess,Math.max(0,cents(original[1]-spent[1])));
    const adjustment=rate(compensado,excess);adjustment[1]=-compensado;
    const values=original.map((v,i)=>cents(v+adjustment[i])), perc=rate(rendaTotal?100:0,values);
    const distribuicao=keys.map((chave,i)=>({chave,valor:values[i],gasto:spent[i],percentual:perc[i],uso:values[i]?spent[i]/values[i]*100:spent[i]?100:0,valorOriginal:original[i],percentualOriginal:d.percentuais[chave],ajustePoupanca:adjustment[i],faixaMin:[50,20,10,10][i],faixaMax:[60,20,20,10][i]}));
    const consumer=expenses.filter(x=>cat(x.categoria).grupo!=='POUPANCA'), gastos=sum(consumer), guardado=spent[1], gastosFixos=sum(consumer.filter(x=>x.tipo==='FIXA'));
    const essenciais=d.essenciaManual??spent[0];
    const alertas=distribuicao.filter(f=>f.gasto>f.valor).map(f=>({tipo:'amarelo',titulo:'Acima do planejado',texto:`O bloco ${GRUPOS[f.chave].titulo} ultrapassou o planejamento. Confira seus lançamentos.`}));
    return {...structuredClone(d),mesAtual:m,configurado:d.salario>0,totalEntradas,rendaTotal,gastos,guardado,gastosFixos,gastosVariaveis:cents(gastos-gastosFixos),percFixos:rendaTotal?gastosFixos/rendaTotal*100:0,percComprometido:rendaTotal?(gastos+guardado)/rendaTotal*100:0,disponivel:0,saldoPlanejado:cents(distribuicao.reduce((s,f)=>s+Math.max(0,f.valor-f.gasto),0)),distribuicao,alertas,recomendacoes:[],
      categorias:DEMO_CATEGORIAS.map(c=>({...c,categoria:c.nome,valor:sum(expenses.filter(x=>x.categoria===c.nome))})).filter(c=>c.valor>0),
      reserva:{essenciais,automatico:d.essenciaManual===null,tresMeses:cents(essenciais*3),seisMeses:cents(essenciais*6)},
      metas:d.metas.map(g=>({...g,progresso:g.valorNecessario?Math.min(100,g.jaTenho/g.valorNecessario*100):0,texto:g.jaTenho>=g.valorNecessario?'Objetivo alcançado!':g.porMes>0?`Mantendo este ritmo, faltam aproximadamente ${Math.ceil((g.valorNecessario-g.jaTenho)/g.porMes)} meses.`:'Defina quanto pretende guardar por mês.'}))};
  }
  async function request(url, method='GET', body, month) {
    const u=new URL(url,'https://demo.invalid'), m=u.searchParams.get('mes')||month;
    if(!/^\d{4}-(0[1-9]|1[0-2])$/.test(m)) throw Error('Escolha um mês válido.');
    const route=u.pathname;
    if(route==='/api/categorias') return structuredClone(DEMO_CATEGORIAS);
    if(route==='/api/meses') return Object.keys(meses).sort().map(mes=>({mes,historicoIncompleto:false}));
    const d=meses[m] ||= seed(m,true);
    if(method==='GET'&&route==='/api/painel') return calc(m);
    if(route==='/api/salario') d.salario=body.salario;
    else if(route==='/api/percentuais') {if(keys.reduce((s,k)=>s+body[k],0)!==100)throw Error('Os percentuais devem somar 100%.');d.percentuais={...body};}
    else if(route==='/api/essenciais') d.essenciaManual=body.valor;
    else {
      const match=route.match(/^\/api\/(despesas|entradas|metas)(?:\/([^/]+))?(\/pagamento)?$/);
      if(!match) throw Error('Recurso não disponível na demonstração.');
      const [,kind,id,payment]=match, list=d[kind], index=list.findIndex(x=>x.id===id);
      if(id&&index<0) throw Error('Registro não encontrado.');
      if(payment) {d.despesasPagas=d.despesasPagas.filter(x=>x!==id);if(body.pago)d.despesasPagas.push(id);}
      else if(method==='DELETE') {list.splice(index,1);d.despesasPagas=d.despesasPagas.filter(x=>x!==id);}
      else {
        if(!body.nome?.trim())throw Error('Informe uma descrição.');
        if(body.data&&!body.data.startsWith(m))throw Error('Escolha uma data dentro do mês selecionado.');
        for(const k of ['valor','valorNecessario','jaTenho','porMes']) if(body[k]!=null&&(!Number.isFinite(body[k])||body[k]<0||body[k]>100000000)) throw Error('Informe um valor válido.');
        const item={...body,nome:body.nome.trim(),id:id||'demo-'+(++seq)};
        if(method==='POST')list.push(item);else if(method==='PUT')list[index]=item;else throw Error('Ação indisponível.');
      }
    }
    return calc(m);
  }
  reset(); return {request,reset,calc};
})();
