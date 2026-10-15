// Ícones locais mantêm a navegação independente de serviços externos.
desenharIcones = function () {
  const paths={
    'layout-dashboard':'M3 3h7v7H3z M14 3h7v4h-7z M14 11h7v10h-7z M3 14h7v7H3z',
    wallet:'M20 8V5H5a2 2 0 0 0 0 4h16v11H5a2 2 0 0 1-2-2V7 M21 12h-5v4h5',
    target:'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18 M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10 M12 11v2',
    'sliders-horizontal':'M3 6h5m4 0h9 M3 12h10m4 0h4 M3 18h3m4 0h11 M8 3v6 M17 9v6 M6 15v6',
    'calendar-days':'M5 5h14a2 2 0 0 1 2 2v13H3V7a2 2 0 0 1 2-2 M3 10h18 M8 3v4 M16 3v4 M7 14h2m3 0h2m3 0h1 M7 17h2m3 0h2',
    plus:'M12 5v14 M5 12h14',pencil:'m15 4 5 5 M4 16 16 4l4 4L8 20H4z',
    'arrow-down-left':'M18 6 6 18 M6 6v12h12','arrow-up-right':'M6 18 18 6 M6 6h12v12',
    'credit-card':'M3 5h18v14H3z M3 10h18 M7 15h3',
    'chevron-down':'m6 9 6 6 6-6','chevron-right':'m9 6 6 6-6 6',
    check:'m5 12 4 4L19 6','trash-2':'M3 6h18 M8 6V3h8v3 M5 6l1 15h12l1-15 M10 10v7 M14 10v7',
    banknote:'M2 5h20v14H2z M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8',receipt:'M5 3v18l3-2 4 2 4-2 3 2V3z M8 7h8 M8 11h8 M8 15h5'
  };
  document.querySelectorAll('i[data-lucide]').forEach(el=>{const svg=document.createElementNS('http://www.w3.org/2000/svg','svg');svg.setAttribute('viewBox','0 0 24 24');svg.setAttribute('width','24');svg.setAttribute('height','24');svg.setAttribute('fill','none');svg.setAttribute('stroke','currentColor');svg.setAttribute('stroke-width','1.8');svg.setAttribute('stroke-linecap','round');svg.setAttribute('stroke-linejoin','round');svg.setAttribute('aria-hidden','true');const p=document.createElementNS(svg.namespaceURI,'path');p.setAttribute('d',paths[el.dataset.lucide]||paths.wallet);svg.append(p);el.replaceWith(svg);});
};
// A conta é demonstrativa; nenhuma senha, foto, assinatura ou dado real é solicitado.
acoes['demo-reset']=()=>{DemoFluxo.reset();state.mes=new Date().toLocaleDateString('sv-SE').slice(0,7);iniciarFinancas();toast('Exemplo restaurado.');};
acoes['demo-perfil']=()=>abrirModal(`<section class="modal-form demo-profile"><div class="between"><h2>Minha conta</h2><button class="icon-btn" data-action="fechar" aria-label="Fechar">×</button></div><div class="foto-perfil"><span class="avatar-grande avatar-letra">V</span><div><strong>Visitante</strong><p class="muted">visitante@example.com</p></div></div><div class="plan-card"><span class="eyebrow">ACESSO DEMONSTRATIVO</span><h3>Explore o Fluxo</h3><p>Esta versão apresenta o aplicativo com dados fictícios. Login, foto de perfil, assinatura e gestão da conta dependem do serviço completo e não são realizados aqui.</p></div><button class="btn ghost" data-action="fechar">Voltar ao aplicativo</button></section>`);
const demoProfile=document.createElement('button');demoProfile.className='btn account-button';demoProfile.dataset.action='demo-perfil';demoProfile.textContent='Visitante';document.querySelector('.app-toolbar').append(demoProfile);
iniciarFinancas();
