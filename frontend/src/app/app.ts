import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NoticeRegion } from './core/notices/notice-region';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, NoticeRegion],
  template: `<router-outlet /><app-notice-region />`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {}
