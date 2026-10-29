import { expect, test } from '@playwright/test';
import { login } from './helpers';

// Cada teste percorre várias telas tirando screenshot de página inteira: no celular passa dos 30 s padrão
test.describe.configure({ timeout: 60_000 });

/** ready: texto que só aparece depois que os dados da tela chegaram. */
const screens: { path: string; title: string; ready?: string }[] = [
  { path: '/painel', title: 'Painel' },
  { path: '/licitacoes', title: 'Licitações' },
  { path: '/orgaos', title: 'Órgãos' },
  { path: '/atas', title: 'Atas', ready: 'Oferecer carona' },
  { path: '/quadros', title: 'Quadros' },
  { path: '/negociacoes', title: 'Negociações', ready: 'no funil' },
  { path: '/tarefas', title: 'Tarefas', ready: 'atrasadas' },
  { path: '/importacao', title: 'Importação' },
  { path: '/usuarios', title: 'Usuários' },
];

test('@screens login', async ({ page }, info) => {
  await page.goto('/login');
  await expect(page.getByRole('heading', { name: 'Entrar' })).toBeVisible();
  await page.screenshot({ path: `screenshots/${info.project.name}/00-login.png`, fullPage: true });
});

test('@screens telas principais', async ({ page, isMobile }, info) => {
  await login(page);
  for (const [index, screen] of screens.entries()) {
    if (isMobile) {
      await page.goto(screen.path);
    } else {
      await page
        .getByRole('navigation', { name: 'Navegação principal' })
        .getByRole('link', { name: screen.title })
        .click();
    }
    await expect(page.getByRole('heading', { level: 1, name: screen.title })).toBeVisible();
    await page.waitForLoadState('networkidle'); // dados da tela carregados antes da captura
    if (screen.ready) await expect(page.getByRole('main').getByText(screen.ready).first()).toBeVisible();
    const name = `${String(index + 1).padStart(2, '0')}-${screen.path.slice(1)}`;
    await page.screenshot({ path: `screenshots/${info.project.name}/${name}.png`, fullPage: true });
  }
  await page.goto('/licitacoes?aba=vencidas');
  await page.locator('tbody tr a').first().click();
  await expect(page.getByRole('tab', { name: 'Histórico' })).toBeVisible();
  await page.screenshot({ path: `screenshots/${info.project.name}/20-licitacao-detalhe.png`, fullPage: true });
  // Contrato em andamento do seed (SEFAZ): itens, pedidos e saldo
  await page.goto('/licitacoes?aba=andamento&q=SEFAZ');
  await page.locator('tbody tr a').first().click();
  await page.getByRole('tab', { name: 'Itens e pedidos' }).click();
  await expect(page.locator('section.contract')).toBeVisible();
  await page.screenshot({ path: `screenshots/${info.project.name}/22-itens-e-pedidos.png`, fullPage: true });
  await page.getByRole('button', { name: 'Registrar pedido' }).click();
  await expect(page.getByRole('dialog', { name: 'Registrar pedido' })).toBeVisible();
  await page.waitForTimeout(300); // fim da animação da gaveta
  await page.screenshot({ path: `screenshots/${info.project.name}/23-registrar-pedido.png` });
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'Importar planilha' }).click();
  await expect(page.getByRole('dialog', { name: 'Importar planilha' })).toBeVisible();
  await page.waitForTimeout(300);
  await page.screenshot({ path: `screenshots/${info.project.name}/24-importar-planilha.png` });
  await page.keyboard.press('Escape');
  await page.goto('/licitacoes');
  await page.getByRole('main').getByRole('button', { name: 'Nova licitação' }).click();
  await expect(page.getByRole('dialog', { name: 'Nova licitação' })).toBeVisible();
  await page.screenshot({ path: `screenshots/${info.project.name}/21-nova-licitacao.png` });
  await page.keyboard.press('Escape');
  if (isMobile) {
    await page.getByRole('button', { name: 'Abrir menu' }).click();
    const nav = page.getByRole('navigation', { name: 'Navegação principal' });
    await expect(nav).toHaveCSS('transform', 'none');
    await page.screenshot({ path: `screenshots/${info.project.name}/99-menu.png` });
  }
});

/** Telas das Fases 3 e 4 num teste à parte: juntas com as outras, a captura passava do tempo limite no celular. */
test('@screens funil, tarefas, atas e caronas', async ({ page }, info) => {
  await login(page);
  // Fase 4: lista do funil, diálogo da negociação e as abas da licitação com tarefas e negociações
  await page.goto('/negociacoes?visao=lista');
  await expect(page.getByRole('main').getByText('no funil')).toBeVisible();
  await page.screenshot({ path: `screenshots/${info.project.name}/25-negociacoes-lista.png`, fullPage: true });
  await page.getByRole('button', { name: 'Carona da ata SEFAZ · material gráfico' }).click();
  await expect(page.getByRole('dialog', { name: 'Negociação' })).toBeVisible();
  await page.waitForTimeout(300);
  await page.screenshot({ path: `screenshots/${info.project.name}/26-negociacao.png` });
  await page.keyboard.press('Escape');
  await page.goto('/licitacoes?aba=andamento&q=SEFAZ');
  await page.locator('tbody tr a').first().click();
  await page.getByRole('tab', { name: 'Negociação' }).click();
  await expect(page.getByRole('button', { name: 'Carona da ata SEFAZ · material gráfico' })).toBeVisible();
  await page.screenshot({ path: `screenshots/${info.project.name}/27-licitacao-negociacoes.png`, fullPage: true });
  await page.getByRole('tab', { name: 'Tarefas' }).click();
  await expect(page.getByRole('button', { name: 'Agendar a entrega do lote de banners' })).toBeVisible();
  await page.screenshot({ path: `screenshots/${info.project.name}/28-licitacao-tarefas.png`, fullPage: true });
  // Fase 3: ata da SEFAZ com itens e carona, o diálogo de carona e os municípios sem ata
  await page.goto('/licitacoes/20000000-0000-4000-8000-000000000013?aba=documentos');
  await expect(page.getByRole('heading', { name: 'Ata ARP 017/2026' })).toBeVisible();
  await page.screenshot({ path: `screenshots/${info.project.name}/29-licitacao-ata.png`, fullPage: true });
  await page.locator('app-ata-section').getByRole('button', { name: 'Registrar carona' }).click();
  await expect(page.getByRole('dialog', { name: 'Registrar carona' })).toBeVisible();
  await page.waitForTimeout(300);
  await page.screenshot({ path: `screenshots/${info.project.name}/30-registrar-carona.png` });
  await page.keyboard.press('Escape');
  await page.goto('/atas?aba=MUNICIPIOS');
  await expect(page.getByRole('row', { name: /Nova Iguaçu/ })).toBeVisible();
  await page.screenshot({ path: `screenshots/${info.project.name}/31-municipios-sem-ata.png`, fullPage: true });
});

/** Fase 5: alertas, a tarefa com o rascunho de e-mail ao órgão e o painel com os indicadores. */
test('@screens alertas e rascunho de e-mail', async ({ page }, info) => {
  await login(page);
  await page.goto('/alertas');
  await expect(page.getByRole('heading', { level: 1, name: 'Alertas' })).toBeVisible();
  await expect(page.getByText('Última verificação')).toBeVisible();
  await page.waitForLoadState('networkidle');
  await page.screenshot({ path: `screenshots/${info.project.name}/32-alertas.png`, fullPage: true });
  await page.goto('/tarefas?q=Sem%20pedido');
  await page.getByRole('button', { name: /^Sem pedido há/ }).first().click();
  await expect(page.getByRole('region', { name: 'Rascunho de e-mail ao órgão' })).toBeVisible();
  await page.waitForTimeout(300); // fim da animação da gaveta
  await page.screenshot({ path: `screenshots/${info.project.name}/33-tarefa-rascunho.png` });
});
