import AxeBuilder from '@axe-core/playwright';
import { expect, Page, test } from '@playwright/test';
import { login } from './helpers';

/**
 * Registro de contato, resposta do órgão e follow-up com IA (Fase 9), com o leitor de demonstração do backend
 * (SHEMA_EXTRACTION_DEMO=true). Cada projeto cria a sua licitação.
 */
test.use({ reducedMotion: 'reduce' });

function isoDaysAgo(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() - days);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

async function signedContract(page: Page): Promise<void> {
  await page.goto('/licitacoes');
  await page.getByRole('main').getByRole('button', { name: 'Nova licitação' }).click();
  const drawer = page.getByRole('dialog', { name: 'Nova licitação' });
  await drawer.getByPlaceholder('Buscar órgão por nome ou município').fill('Petró');
  await drawer.getByRole('option', { name: /Prefeitura Municipal de Petrópolis/ }).click();
  await drawer.getByLabel('Data da vitória').fill(isoDaysAgo(60));
  await drawer.getByLabel('Nº do pregão / processo').fill(`${Math.floor(Math.random() * 900) + 100}/2026`);
  await drawer.getByLabel('Objeto (descritivo da licitação)').fill('Material de expediente (follow-up)');
  await drawer.getByLabel('Tipo de material').fill('Expediente');
  await drawer.getByLabel('Valor total').fill('30.000,00');
  await drawer.getByRole('button', { name: 'Cadastrar licitação' }).click();
  await expect(page.locator('.head .badge')).toHaveText('Vencida');
  await page.getByRole('button', { name: 'Registrar contrato' }).click();
  const dialog = page.getByRole('dialog', { name: 'Registrar contrato' });
  await dialog.getByLabel(/^Data de assinatura/).fill(isoDaysAgo(40));
  await dialog.getByRole('button', { name: 'Registrar contrato' }).click();
  await expect(page.locator('.head .badge')).toHaveText('Contrato assinado');
}

async function audit(page: Page): Promise<void> {
  const axe = await new AxeBuilder({ page })
    .include('[role="dialog"]')
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  expect(axe.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
}

test('a resposta do órgão vira contato e tarefa, e o follow-up sai com contexto', async ({ page }) => {
  test.setTimeout(120_000);
  await login(page);
  await signedContract(page);

  // Registrar contato: a IA entende a resposta colada e sugere a tarefa
  await page.getByRole('tab', { name: 'Histórico' }).click();
  await page.getByRole('button', { name: 'Registrar contato' }).click();
  const contact = page.getByRole('dialog', { name: 'Registrar contato' });
  await contact.getByLabel('Canal').selectOption({ label: 'WhatsApp' });
  await contact
    .getByLabel('Resposta do órgão')
    .fill('Bom dia! O empenho deve sair no mês que vem, depois da liberação do orçamento.');
  await contact.getByRole('button', { name: 'Entender com IA' }).click();
  await expect(contact.getByText('Situação e resumo preenchidos com a leitura da IA.')).toBeVisible({
    timeout: 30_000,
  });
  await expect(contact.getByLabel(/^Situação/)).toHaveValue('PEDIDO_PREVISTO');
  await expect(contact.getByLabel(/^O que foi conversado/)).toHaveValue(/mês que vem/);
  await expect(contact.getByLabel('Tarefa', { exact: true })).toHaveValue(
    'Cobrar o pedido do mês que vem',
  );
  await audit(page);
  await contact.getByRole('button', { name: 'Registrar contato' }).click();
  await expect(contact).toBeHidden();

  // O contato aparece no histórico, e a tarefa na aba Tarefas
  await expect(page.getByText(/registrou contato por\s+WhatsApp/).first()).toBeVisible();
  await expect(page.getByText('Pedem no mês que vem, depois da liberação do orçamento.')).toBeVisible();
  await expect(page.getByText(/sugeridos pela IA e conferidos/).first()).toBeVisible();
  await page.getByRole('tab', { name: 'Tarefas' }).click();
  await expect(page.getByText('Cobrar o pedido do mês que vem')).toBeVisible();

  // Follow-up do contrato: WhatsApp curto, com "Abrir no WhatsApp"
  await page.getByRole('tab', { name: 'Itens e pedidos' }).click();
  await page.getByRole('button', { name: 'Follow-up' }).click();
  const followUp = page.getByRole('dialog', { name: 'Follow-up ao órgão' });
  await followUp.getByRole('radio', { name: 'WhatsApp' }).click();
  await followUp.getByRole('button', { name: 'Escrever com IA' }).click();
  await expect(followUp.getByLabel('Mensagem de WhatsApp')).toHaveValue(/próximo pedido/, {
    timeout: 30_000,
  });
  await expect(followUp.getByRole('link', { name: 'Abrir no WhatsApp' })).toHaveAttribute(
    'href',
    /^https:\/\/wa\.me\//,
  );
  await audit(page);

  // E-mail, com assunto e "Abrir no e-mail"
  await followUp.getByRole('radio', { name: 'E-mail' }).click();
  await followUp.getByRole('button', { name: 'Escrever de novo' }).click();
  await expect(followUp.getByLabel('Assunto')).toHaveValue('Previsão do próximo pedido', {
    timeout: 30_000,
  });
  await expect(followUp.getByRole('link', { name: 'Abrir no e-mail' })).toHaveAttribute(
    'href',
    /^mailto:/,
  );
});
