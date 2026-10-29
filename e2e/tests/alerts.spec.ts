import { expect, Page, test } from '@playwright/test';
import { login } from './helpers';

/** Mailpit do compose: captura o aviso interno dos alertas (ADR 0006). */
const MAILPIT = process.env.E2E_MAILPIT_URL ?? 'http://localhost:18025';

function isoDaysAgo(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() - days);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Licitação vencida há 60 dias, com contrato assinado há 40 e nenhum pedido. Devolve o id da licitação. */
async function signedContractWithoutOrder(page: Page): Promise<string> {
  const processNumber = `${Math.floor(Math.random() * 900) + 100}/2026`;
  await page.goto('/licitacoes');
  await page.getByRole('main').getByRole('button', { name: 'Nova licitação' }).click();
  const drawer = page.getByRole('dialog', { name: 'Nova licitação' });
  await drawer.getByPlaceholder('Buscar órgão por nome ou município').fill('Petró');
  await drawer.getByRole('option', { name: /Prefeitura Municipal de Petrópolis/ }).click();
  await drawer.getByLabel('Data da vitória').fill(isoDaysAgo(60));
  await drawer.getByLabel('Nº do pregão / processo').fill(processNumber);
  await drawer.getByLabel('Objeto (descritivo da licitação)').fill('Faixas e banners para a campanha de vacinação');
  await drawer.getByLabel('Tipo de material').fill('Material gráfico');
  await drawer.getByLabel('Valor total').fill('48.000,00');
  await drawer.getByRole('button', { name: 'Cadastrar licitação' }).click();
  await expect(page.locator('.head .badge')).toHaveText('Vencida');

  await page.getByRole('button', { name: 'Registrar contrato' }).click();
  const dialog = page.getByRole('dialog', { name: 'Registrar contrato' });
  await dialog.getByLabel('Data de assinatura').fill(isoDaysAgo(40));
  await dialog.getByRole('button', { name: 'Registrar contrato' }).click();
  await expect(page.locator('.head .badge')).toHaveText('Contrato assinado');
  return page.url().match(/licitacoes\/([0-9a-f-]{36})/)![1];
}

test('contrato assinado há mais de 30 dias sem pedido gera tarefa com rascunho e aviso por e-mail', async ({
  page,
  request,
}) => {
  await login(page);
  const tenderId = await signedContractWithoutOrder(page);

  // Verificar agora (desktop e celular rodam juntos: se o outro está verificando, tenta de novo)
  await page.goto('/alertas');
  await expect(page.getByText('Contrato assinado há 30 dias ou mais sem nenhum pedido')).toBeVisible();
  await expect(async () => {
    await page.getByRole('button', { name: 'Verificar agora' }).click();
    await expect(page.locator('.status-region')).toContainText('Verificação concluída', { timeout: 5_000 });
  }).toPass({ timeout: 30_000 });

  // A fila cria a tarefa em segundo plano: espera o estado final na aba Tarefas da licitação
  await page.goto(`/licitacoes/${tenderId}?aba=tarefas`);
  const task = page.getByRole('button', { name: 'Sem pedido há 40 dias: Prefeitura Municipal de Petrópolis' });
  await expect(async () => {
    await page.reload();
    await expect(task).toBeVisible({ timeout: 2_000 });
  }).toPass({ timeout: 30_000 });

  await task.click();
  const dialog = page.getByRole('dialog', { name: 'Tarefa' });
  await expect(dialog.getByText('Criada por alerta')).toBeVisible();
  const draft = dialog.getByRole('region', { name: 'Rascunho de e-mail ao órgão' });
  await expect(draft).toContainText('Previsão de pedidos: Contrato');
  await expect(draft.getByLabel('Texto do e-mail')).toContainText('Visto que participamos do certame');
  await expect(draft.getByRole('link', { name: 'Abrir no e-mail' })).toHaveAttribute('href', /^mailto:/);
  await page.keyboard.press('Escape');

  // Aviso interno para o responsável, com o link da licitação (Mailpit)
  await expect
    .poll(
      async () => {
        const res = await request.get(`${MAILPIT}/api/v1/search?query=${tenderId}`);
        const body = await res.json();
        return (body.messages ?? []).map((m: { Subject: string }) => m.Subject);
      },
      { timeout: 30_000 },
    )
    .toContain('[LicitaOne] Contrato sem pedido há 40 dias: Prefeitura Municipal de Petrópolis');

  // O pedido chega: a tarefa do alerta se conclui sozinha
  await page.getByRole('tab', { name: 'Itens e pedidos' }).click();
  await page.getByRole('button', { name: 'Registrar pedido' }).click();
  const order = page.getByRole('dialog', { name: 'Registrar pedido' });
  await order.getByLabel('Empenho, AF ou ordem de compra').fill('AF 090/2026');
  await order.getByLabel('Valor do pedido').fill('8.000,00');
  await order.getByRole('button', { name: 'Registrar pedido' }).click();
  await expect(page.locator('.head .badge')).toHaveText('Em andamento');

  await expect(async () => {
    await page.goto(`/licitacoes/${tenderId}?aba=tarefas`);
    await task.click({ timeout: 2_000 });
    await expect(page.getByRole('dialog', { name: 'Tarefa' })).toContainText('Concluída sozinha', {
      timeout: 2_000,
    });
  }).toPass({ timeout: 30_000 });
});

test('alertas mostram as regras, o que foi gerado e levam à licitação', async ({ page }) => {
  await login(page);
  await page.goto('/alertas');
  await expect(page.getByRole('heading', { level: 1, name: 'Alertas' })).toBeVisible();
  await expect(page.getByText('Prazo a confirmar')).toBeVisible();
  await page.getByRole('tab', { name: 'Contrato sem pedido' }).click();
  await expect(page).toHaveURL(/regra=CONTRATO_SEM_PEDIDO/);
  const first = page.locator('tbody tr').first().getByRole('link').first();
  await expect(first).toContainText('Sem pedido há');
  await first.click();
  await expect(page).toHaveURL(/licitacoes\/[0-9a-f-]{36}/);
});
