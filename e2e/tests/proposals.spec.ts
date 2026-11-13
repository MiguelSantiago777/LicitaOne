import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { login } from './helpers';

test('proposta nasce com número, salva sozinha, calcula o total e baixa o PDF; duplicar e cancelar mantêm os números', async ({
  page,
}) => {
  test.setTimeout(90_000);
  await login(page);
  await page.goto('/propostas');
  await expect(page.getByRole('heading', { level: 1, name: 'Propostas de preços' })).toBeVisible();

  // "Nova proposta" já cria a proposta com o número do ano
  await page.getByRole('button', { name: 'Nova proposta' }).click();
  const heading = page.getByRole('heading', { level: 1, name: /^Proposta nº \d{4}\/\d{4}$/ });
  await expect(heading).toBeVisible();
  const number = (await heading.textContent())!.replace('Proposta nº', '').trim();
  await expect(page.getByText('Em preenchimento')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Baixar PDF' })).toBeDisabled();

  const client = `Construtora E2E ${Date.now().toString(36)}`;
  await page.getByLabel('Nome ou razão social').fill(client);
  await page.getByLabel('CNPJ').fill('11222333000181');
  await page.getByLabel('Telefones').fill('(21) 2222-0000');
  await page.getByLabel('Endereço').fill('Rua da Assembleia, 10, Centro, Rio de Janeiro/RJ');
  await page.getByLabel('E-mail').fill('compras@construtora.com.br');
  await page.getByRole('radio', { name: 'Serviço' }).check();

  await page.getByRole('button', { name: 'Adicionar item' }).click();
  await expect(page.getByLabel('Descrição do item 1')).toBeFocused();
  await page.getByLabel('Descrição do item 1').fill('Manutenção preventiva mensal');
  await page.getByLabel('Unidade do item 1').fill('mês');
  await page.getByLabel('Quantidade do item 1').fill('12');
  await page.getByLabel('Valor unitário do item 1').fill('1.500,00');
  await page.getByRole('button', { name: 'Adicionar item' }).click();
  await page.getByLabel('Descrição do item 2').fill('Visita técnica extra');
  await page.getByLabel('Quantidade do item 2').fill('3');
  await page.getByLabel('Valor unitário do item 2').fill('250,5');
  await page.getByLabel('Valor unitário do item 2').blur();
  await expect(page.getByLabel('Valor unitário do item 2')).toHaveValue('250,50');

  await page.getByLabel('Validade da proposta (dias)').fill('60');
  await page.getByLabel('Forma de pagamento').fill('30 dias após o atesto da nota fiscal');
  await page.getByLabel('Prazo de entrega').fill('Início em até 5 dias após a ordem de serviço');
  await page.getByLabel('Cargo').fill('Gerente comercial');
  await page.getByLabel('CPF').fill('52998224725');
  await page.getByLabel('CPF').blur();
  await expect(page.getByLabel('CPF')).toHaveValue('529.982.247-25');

  // Salvo pelo servidor: total, extenso e pronta para o PDF
  await expect(page.getByRole('status').filter({ hasText: 'Salvo' })).toBeVisible();
  const summary = page.getByRole('region', { name: 'Resumo' });
  await expect(summary.getByText('R$ 18.751,50')).toBeVisible();
  await expect(summary.getByText('dezoito mil setecentos e cinquenta e um reais e cinquenta centavos')).toBeVisible();
  await expect(page.getByText('Pronta para o PDF')).toBeVisible();

  const audit = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  expect(audit.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target).join(' | ')}`)).toEqual([]);

  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Baixar PDF' }).click();
  expect((await download).suggestedFilename()).toBe(`Proposta-${number.replace('/', '-')}.pdf`);

  // Recarregar mostra o que foi salvo, com o mesmo número
  await page.reload();
  await expect(page.getByRole('heading', { level: 1, name: `Proposta nº ${number}` })).toBeVisible();
  await expect(page.getByLabel('Nome ou razão social')).toHaveValue(client);
  await expect(page.getByLabel('Quantidade do item 1')).toHaveValue('12');

  // Duplicar: número novo, mesmo conteúdo
  await page.getByRole('button', { name: 'Duplicar' }).click();
  await expect(page.getByRole('heading', { level: 1, name: /^Proposta nº / })).not.toHaveText(
    `Proposta nº ${number}`,
  );
  await expect(page.getByLabel('Nome ou razão social')).toHaveValue(client);
  const copyNumber = (await page.getByRole('heading', { level: 1 }).textContent())!
    .replace('Proposta nº', '')
    .trim();

  // Cancelar: continua na lista só com "Mostrar canceladas"
  await page.getByRole('button', { name: 'Cancelar proposta' }).click();
  await page.getByRole('button', { name: 'Confirmar cancelamento' }).click();
  await expect(page.getByText('Cancelada', { exact: true })).toBeVisible();
  await expect(page.getByLabel('Nome ou razão social')).toHaveAttribute('readonly', '');

  await page.goto('/propostas');
  await page.getByLabel('Buscar propostas').fill(client);
  await expect(page.getByRole('link', { name: number })).toBeVisible();
  await expect(page.getByRole('link', { name: copyNumber })).toHaveCount(0);
  await page.getByRole('button', { name: 'Mostrar canceladas' }).click();
  await expect(page.getByRole('link', { name: copyNumber })).toBeVisible();

  // Clicar em qualquer parte da linha (aqui, no nome do cliente) abre a proposta
  await page.getByRole('row').filter({ hasText: number }).getByText(client).click();
  await expect(page.getByRole('heading', { level: 1, name: `Proposta nº ${number}` })).toBeVisible();
});
