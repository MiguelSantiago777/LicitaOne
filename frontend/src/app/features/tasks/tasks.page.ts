import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-tasks-page',
  template: `
    <div class="title-row">
      <div>
        <h1>Tarefas</h1>
        <p class="sub">Follow-ups manuais e os gerados pelos alertas.</p>
      </div>
    </div>
    <div class="table-wrap">
      <table>
        <caption class="visually-hidden">
          Tarefas
        </caption>
        <thead>
          <tr>
            <th scope="col">Tarefa</th>
            <th scope="col">Situação</th>
            <th scope="col" class="n">Data e hora</th>
            <th scope="col">Responsável</th>
            <th scope="col">Negociação</th>
            <th scope="col" class="n">Valor</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td colspan="6">
              <div class="empty">
                <strong>Nenhuma tarefa aberta.</strong>
                <span>A lista de tarefas chega na Fase 4.</span>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  `,
  styles: `
    @media (max-width: 640px) {
      thead {
        display: none;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TasksPage {}
