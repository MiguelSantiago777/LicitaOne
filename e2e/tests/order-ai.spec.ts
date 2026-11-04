import AxeBuilder from '@axe-core/playwright';
import { expect, Page, test } from '@playwright/test';
import { login } from './helpers';

/**
 * Contrato e pedido com IA (Fase 8), com o leitor de demonstração do backend (SHEMA_EXTRACTION_DEMO=true): o contrato
 * vem do PDF com os itens, e o pedido vem de um documento (AF) ou de uma mensagem colada. Cada projeto cria a sua
 * licitação, então desktop e celular não disputam dados.
 */
test.use({ reducedMotion: 'reduce' });

function isoDaysAgo(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() - days);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

async function wonTender(page: Page): Promise<void> {
  await page.goto('/licitacoes');
  await page.getByRole('main').getByRole('button', { name: 'Nova licitação' }).click();
  const drawer = page.getByRole('dialog', { name: 'Nova licitação' });
  await drawer.getByPlaceholder('Buscar órgão por nome ou município').fill('Petró');
  await drawer.getByRole('option', { name: /Prefeitura Municipal de Petrópolis/ }).click();
  await drawer.getByLabel('Data da vitória').fill(isoDaysAgo(30));
  await drawer.getByLabel('Nº do pregão / processo').fill(`${Math.floor(Math.random() * 900) + 100}/2026`);
  await drawer.getByLabel('Objeto (descritivo da licitação)').fill('Faixas e banners (leitura com IA)');
  await drawer.getByLabel('Tipo de material').fill('Material gráfico');
  await drawer.getByLabel('Valor total').fill('48.000,00');
  await drawer.getByRole('button', { name: 'Cadastrar licitação' }).click();
  await expect(page.locator('.head .badge')).toHaveText('Vencida');
}

async function upload(page: Page, kind: string, title: string, fileName: string): Promise<void> {
  await page.getByRole('tab', { name: 'Documentos' }).click();
  await page.getByRole('button', { name: 'Enviar documento' }).click();
  const dialog = page.getByRole('dialog', { name: 'Enviar documento' });
  await dialog.getByLabel('Tipo').selectOption({ label: kind });
  await dialog.getByLabel('Arquivo').setInputFiles({
    name: fileName,
    mimeType: 'application/pdf',
    buffer: Buffer.from(`%PDF-1.7\n${title}\n%%EOF`),
  });
  await dialog.getByLabel('Título').fill(title);
  await dialog.getByRole('button', { name: 'Enviar' }).click();
  await expect(dialog).toBeHidden();
}

async function audit(page: Page): Promise<void> {
  const axe = await new AxeBuilder({ page })
    .include('[role="dialog"]')
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  expect(axe.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
}

test('a IA preenche o contrato e o pedido, e o pedido guarda o documento de origem', async ({ page }) => {
  test.setTimeout(120_000);
  await login(page);
  await wonTender(page);

  // Contrato: licitação sem PDF nenhum; o PDF é enviado pela própria caixa da IA, que já pede a leitura
  await page.getByRole('button', { name: 'Registrar contrato' }).click();
  const contract = page.getByRole('dialog', { name: 'Registrar contrato' });
  await expect(contract.getByText(/ainda não tem o PDF do contrato/)).toBeVisible();
  await contract.getByLabel('Enviar o PDF do contrato para a IA ler').setInputFiles({
    name: 'Contrato CT 031-2026.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.7\ncontrato\n%%EOF'),
  });
  await expect(contract.getByText('Campos preenchidos com a leitura da IA.')).toBeVisible({
    timeout: 30_000,
  });
  await expect(contract.getByLabel(/^Número/)).toHaveValue('CT 031/2026');
  await expect(contract.getByLabel(/^Data de assinatura/)).toHaveValue(isoDaysAgo(10));
  await expect(contract.getByLabel(/^Valor do contrato/)).toHaveValue('48.000,00');
  await expect(contract.getByText('IA · p. 2')).toBeVisible();
  await expect(contract.getByText(/os 2 itens lidos aparecem/)).toBeVisible();
  await audit(page);
  await contract.getByRole('button', { name: 'Registrar contrato' }).click();
  await expect(contract).toBeHidden();

  // Os itens lidos abrem para conferir
  const items = page.getByRole('dialog', { name: 'Itens do contrato' });
  await expect(items.getByText('Itens lidos pela IA no PDF do contrato.')).toBeVisible();
  await expect(items.getByLabel('Descrição do item 1')).toHaveValue('Faixa em lona 3 x 0,70 m');
  await expect(items.getByLabel('Quantidade do item 2')).toHaveValue('800');
  await items.getByRole('button', { name: 'Salvar itens' }).click();
  await expect(items).toBeHidden();

  // Pedido por documento: a AF enviada como Empenho
  await upload(page, 'Empenho', 'AF 077/2026', 'af-077-2026.pdf');
  await page.getByRole('tab', { name: 'Itens e pedidos' }).click();
  await page.getByRole('button', { name: 'Registrar pedido' }).click();
  const order = page.getByRole('dialog', { name: 'Registrar pedido' });
  await expect(order.getByRole('heading', { name: 'Preencher com IA' })).toBeVisible();
  await expect(order.getByLabel('Pedido para a IA ler')).toContainText('AF 077/2026');
  await order.getByRole('button', { name: 'Ler com IA' }).click();
  await expect(order.getByText('Pedido preenchido com a leitura da IA.')).toBeVisible({
    timeout: 30_000,
  });
  await expect(order.getByLabel(/^Empenho, AF ou ordem de compra/)).toHaveValue('AF 077/2026');
  await expect(order.getByLabel(/Faixa em lona/)).toHaveValue('10');
  await expect(order.getByLabel(/Banner em lona/)).toHaveValue('5');
  await expect(order.getByText(/Cartucho de toner 85A/)).toBeVisible();
  await expect(order.getByText('A assinatura do ofício está ilegível.')).toBeVisible();
  await expect(order.getByLabel('Observações')).toHaveValue(/Secretaria de Educação/);
  await audit(page);
  await order.getByRole('button', { name: 'Registrar pedido' }).click();
  await expect(order).toBeHidden();
  await expect(page.locator('.head .badge')).toHaveText('Em andamento');
  await expect(page.getByRole('button', { name: 'AF 077/2026' })).toBeVisible();

  // Pedido por mensagem colada: preenche e a pessoa decide (aqui, cancela)
  await page.getByRole('button', { name: 'Registrar pedido' }).click();
  await order.getByRole('radio', { name: 'Mensagem colada' }).click();
  await order
    .getByLabel('E-mail ou mensagem do órgão')
    .fill('Bom dia! Pedimos 10 faixas e 5 banners conforme a AF 077/2026. Obrigada.');
  await order.getByRole('button', { name: 'Ler com IA' }).click();
  await expect(order.getByText('Pedido preenchido com a leitura da IA.')).toBeVisible({
    timeout: 30_000,
  });
  await expect(order.getByLabel(/Faixa em lona/)).toHaveValue('10');
  await expect(order.getByLabel('E-mail ou mensagem do órgão')).toHaveValue('');
  await order.getByRole('button', { name: 'Cancelar' }).click();

  // O histórico guarda a origem e que veio da IA
  await page.getByRole('tab', { name: 'Histórico' }).click();
  await expect(
    page.getByText(/Pedido de .*AF 077\/2026.*documento "AF 077\/2026".*sugeridos pela IA/).first(),
  ).toBeVisible();
  await expect(page.getByText(/Contrato CT 031\/2026 de .*sugeridos pela IA/).first()).toBeVisible();
  // o PDF enviado pela caixa ficou guardado em Documentos
  await page.getByRole('tab', { name: 'Documentos' }).click();
  await expect(page.getByText('Contrato CT 031-2026').first()).toBeVisible();
});
