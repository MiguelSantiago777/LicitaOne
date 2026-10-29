import { expect, test } from '@playwright/test';
import { login } from './helpers';

test('negociação nasce da licitação, ganha tarefa atrasada, anda no funil e a tarefa é concluída na lista', async ({
  page,
  isMobile,
}) => {
  await login(page);
  await page.goto('/negociacoes');
  const title = `Carona E2E ${Date.now().toString(36)}`;
  const semContato = page.getByRole('region', { name: 'Sem contato' });
  const contato = page.getByRole('region', { name: 'Contato feito' });

  // nova negociação a partir da ata da SEFAZ: o valor vem da licitação
  await page.getByRole('button', { name: 'Nova negociação', exact: true }).click();
  const dealDialog = page.getByRole('dialog', { name: 'Nova negociação' });
  await dealDialog.getByLabel('Título').fill(title);
  await dealDialog.getByLabel('Licitação de origem').fill('SEFAZ');
  await dealDialog.getByRole('button', { name: /SEFAZ · Secretaria de Fazenda/ }).first().click();
  await expect(dealDialog.getByLabel('Valor (R$)')).toHaveValue('237.000,00');
  await dealDialog.getByLabel('Prioridade').selectOption({ label: 'Alta' });
  await dealDialog.getByRole('button', { name: 'Criar negociação' }).click();
  await expect(dealDialog).toBeHidden();
  const card = semContato.getByRole('article', { name: new RegExp(title) });
  await expect(card).toBeVisible();
  await expect(card).toBeFocused();
  await expect(card.getByText('Nova')).toBeVisible();

  // tarefa vencida criada pelo card vira a faixa de atraso
  await card.getByRole('button', { name: 'Criar tarefa' }).click();
  const taskDialog = page.getByRole('dialog', { name: 'Nova tarefa' });
  await expect(taskDialog.getByText(title)).toBeVisible();
  const taskTitle = `Ligar para compras ${title}`;
  await taskDialog.getByLabel('Tarefa', { exact: true }).fill(taskTitle);
  await taskDialog.getByLabel('Data e hora').fill('2026-01-10T09:00');
  await taskDialog.getByRole('button', { name: 'Criar tarefa' }).click();
  await expect(taskDialog).toBeHidden();
  await expect(card.locator('.overdue')).toContainText('10/01');
  await expect(card.locator('.overdue')).toContainText(taskTitle);

  // anda no funil: arrastando no desktop, pelo campo "Etapa" no celular
  if (isMobile) {
    await card.click();
    const editDialog = page.getByRole('dialog', { name: 'Negociação' });
    await editDialog.getByLabel('Etapa').selectOption({ label: 'Contato feito' });
    await editDialog.getByRole('button', { name: 'Salvar' }).click();
    await expect(editDialog).toBeHidden();
  } else {
    await card.evaluate((el) => el.scrollIntoView({ block: 'center' }));
    const from = await card.boundingBox();
    const to = await contato.locator('.column-body').boundingBox();
    await page.mouse.move(from!.x + 20, from!.y + 20);
    await page.mouse.down();
    await page.mouse.move(from!.x + 60, from!.y + 30, { steps: 5 });
    // mesma altura de onde saiu: as colunas têm a altura da maior
    await page.mouse.move(to!.x + 40, from!.y + 20, { steps: 20 });
    await page.mouse.up();
  }
  await expect(contato.getByRole('article', { name: new RegExp(title) })).toBeVisible();
  await expect(semContato.getByRole('article', { name: new RegExp(title) })).toHaveCount(0);

  // a busca do funil acha pelo título e a soma da etapa segue o filtro
  await page.getByLabel('Buscar negociações').fill(title);
  await expect(page).toHaveURL(/q=Carona/);
  await expect(contato.locator('.column-value')).toHaveText('R$ 237.000');

  // na lista de tarefas: atrasada, com a negociação e o valor; conclui em massa
  await page.goto('/tarefas');
  await page.getByLabel('Buscar tarefas').fill(taskTitle);
  const row = page.getByRole('row', { name: new RegExp(taskTitle) });
  await expect(row.getByText('Atrasada')).toBeVisible();
  await expect(row.getByRole('link', { name: title })).toBeVisible();
  await expect(row.getByText('R$ 237.000,00')).toBeVisible();
  await row.getByRole('checkbox').check();
  await page.getByRole('button', { name: 'Concluir 1' }).click();
  await expect(page.getByRole('main').getByRole('status')).toHaveText(/1 tarefa concluída/);
  await expect(row).toHaveCount(0);
  await page.getByRole('tab', { name: 'Concluídas' }).click();
  await expect(row.getByText('Concluída')).toBeVisible();

  // limpeza: sem tarefa aberta, a negociação pode ser apagada
  await page.goto(`/negociacoes?q=${encodeURIComponent(title)}`);
  await contato.getByRole('article', { name: new RegExp(title) }).click();
  const editDialog = page.getByRole('dialog', { name: 'Negociação' });
  await expect(editDialog.getByLabel('Título')).toHaveValue(title);
  await editDialog.getByRole('button', { name: 'Apagar' }).click();
  await editDialog.getByRole('button', { name: 'Apagar' }).click();
  await expect(editDialog).toBeHidden();
  await expect(contato.getByText('Nada com este filtro')).toBeVisible();
});
