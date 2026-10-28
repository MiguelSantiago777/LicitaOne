import { expect, Page, test } from '@playwright/test';
import { login } from './helpers';

/** Cadastra uma licitação já vencida em Petrópolis e abre o detalhe dela. */
async function createWonTender(page: Page): Promise<void> {
  const processNumber = `${Math.floor(Math.random() * 900) + 100}/2026`;
  await page.goto('/licitacoes');
  await page.getByRole('main').getByRole('button', { name: 'Nova licitação' }).click();

  const drawer = page.getByRole('dialog', { name: 'Nova licitação' });
  await drawer.getByPlaceholder('Buscar órgão por nome ou município').fill('Petró');
  await drawer.getByRole('option', { name: /Prefeitura Municipal de Petrópolis/ }).click();
  await drawer.getByLabel('Nº do pregão / processo').fill(processNumber);
  await drawer.getByLabel('Objeto (descritivo da licitação)').fill('Banners e adesivos perfurados para campanha');
  await drawer.getByLabel('Tipo de material').fill('Material gráfico');
  await drawer.getByLabel('Valor total').fill('123.456,78');
  await drawer.getByRole('button', { name: 'Cadastrar licitação' }).click();

  await expect(page.getByRole('heading', { level: 1, name: 'Prefeitura Municipal de Petrópolis' })).toBeVisible();
  await expect(page.getByText(`PE ${processNumber.padStart(8, '0')}`)).toBeVisible();
  await expect(page.locator('.head .badge')).toHaveText('Vencida');
}

/** Registra o contrato pelo botão do cabeçalho, com o valor sugerido (o da licitação). */
async function signContract(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Registrar contrato' }).click();
  const dialog = page.getByRole('dialog', { name: 'Registrar contrato' });
  await expect(dialog.getByLabel('Valor do contrato')).toHaveValue('123.456,78');
  await dialog.getByRole('button', { name: 'Registrar contrato' }).click();
  await expect(page.locator('.head .badge')).toHaveText('Contrato assinado');
}

test('licitação vencida → contrato → pedido → saldo atualizado e histórico', async ({ page }) => {
  await login(page);
  await createWonTender(page);
  await signContract(page);

  await expect(page.getByRole('tab', { name: 'Itens e pedidos' })).toHaveAttribute('aria-selected', 'true');
  await page.getByRole('button', { name: 'Registrar pedido' }).click();
  const order = page.getByRole('dialog', { name: 'Registrar pedido' });
  await order.getByLabel('Empenho, AF ou ordem de compra').fill('AF 001/2026');
  await order.getByLabel('Valor do pedido').fill('23.456,78');
  await order.getByRole('button', { name: 'Registrar pedido' }).click();

  await expect(page.locator('.head .badge')).toHaveText('Em andamento');
  const contract = page.locator('section.contract');
  await expect(contract.locator('.remaining')).toHaveText('R$ 100.000,00');
  await expect(contract.getByText('AF 001/2026')).toBeVisible();

  await page.getByRole('tab', { name: 'Histórico' }).click();
  await expect(page.getByText('mudou de Vencida para Contrato assinado')).toBeVisible();
  await expect(page.locator('ol.history')).toContainText('registrou um pedido: Pedido de R$ 23.456,78');
  await expect(page.getByText('cadastrou a licitação como Vencida')).toBeVisible();
});

test('registrar contrato sem data não é aceito e a mensagem explica o porquê', async ({ page }) => {
  await login(page);
  await page.goto('/licitacoes?aba=vencidas&q=Cantagalo');
  await page.getByRole('link', { name: 'Prefeitura Municipal de Cantagalo' }).click();
  await page.getByRole('button', { name: 'Registrar contrato' }).click();
  const dialog = page.getByRole('dialog', { name: 'Registrar contrato' });
  await dialog.getByLabel('Data de assinatura').fill('');
  await dialog.getByRole('button', { name: 'Registrar contrato' }).click();
  await expect(dialog.getByRole('alert')).toContainText('Informe a data de assinatura');
});

test('importa planilha com linhas válidas e inválidas e mostra o resultado', async ({ page }) => {
  await login(page);
  await createWonTender(page);
  await signContract(page);

  await page.getByRole('button', { name: 'Importar planilha' }).click();
  const wizard = page.getByRole('dialog', { name: 'Importar planilha' });
  const csv = [
    'Descrição;Quantidade;V.U.;Pedido qtd',
    'Banner lona 440 g;100;75,00;40',
    'Adesivo perfurado;200;18;',
    'Cartaz A2;setenta e dois;8;',
  ].join('\n');
  await wizard.getByTestId('import-file').setInputFiles({
    name: 'itens.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from(csv, 'utf-8'),
  });
  await wizard.getByRole('button', { name: 'Continuar' }).click();

  // colunas reconhecidas pelo título
  await expect(wizard.getByLabel('Descrição')).toHaveValue('0');
  await expect(wizard.getByLabel('Valor unitário')).toHaveValue('2');
  await wizard.getByRole('button', { name: 'Ver prévia' }).click();

  await expect(wizard.getByText('2 novos')).toBeVisible();
  await expect(wizard.getByText('1 com erro')).toBeVisible();
  await expect(wizard.getByText('Quantidade não é um número: "setenta e dois".')).toBeVisible();
  await expect(wizard.locator('.total-line strong')).toHaveText('R$ 3.000,00');
  await wizard.getByRole('button', { name: 'Importar 2 linhas' }).click();

  await expect(wizard.getByText('Importação concluída')).toBeVisible({ timeout: 30_000 });
  await expect(wizard.locator('.result')).toContainText('2 de 3 linhas importadas · 1 com erro');
  await wizard.getByRole('button', { name: 'Fechar' }).last().click();

  const contract = page.locator('section.contract');
  await expect(contract.getByText('Banner lona 440 g').filter({ visible: true }).first()).toBeVisible();
  await expect(contract.locator('.remaining')).toHaveText('R$ 120.456,78');
  await expect(page.locator('.head .badge')).toHaveText('Em andamento');
});

test('busca e filtros ficam na URL e mudam os contadores', async ({ page }) => {
  await login(page);
  await page.goto('/licitacoes?aba=vencidas');
  await page.getByPlaceholder('Órgão, município, UASG, pregão ou material').fill('Mesquita');
  await expect(page).toHaveURL(/q=Mesquita/);
  await expect(page.getByRole('link', { name: 'Prefeitura Municipal de Mesquita' })).toBeVisible();
  await expect(page.locator('tbody tr')).toHaveCount(1);
  await expect(page.getByRole('tab', { name: /Vencidas/ }).locator('.count')).toHaveText('1');

  await page.reload();
  await expect(page.getByRole('link', { name: 'Prefeitura Municipal de Mesquita' })).toBeVisible();
});
