import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import {
  ArrowUpRight,
  Bell,
  Building2,
  CalendarClock,
  ChevronDown,
  ChevronRight,
  CircleAlert,
  CircleCheck,
  CircleHelp,
  Clock,
  Columns3,
  Download,
  Ellipsis,
  FileSignature,
  FileSpreadsheet,
  FileText,
  Handshake,
  Hourglass,
  Landmark,
  LayoutDashboard,
  List,
  ListChecks,
  ListFilter,
  LogOut,
  Mail,
  Menu,
  PiggyBank,
  Plus,
  ScrollText,
  Search,
  Settings,
  SquareKanban,
  Star,
  TrendingUp,
  TriangleAlert,
  Upload,
  Wallet,
  X,
  type IconNode,
} from 'lucide';

/** Ícones Lucide (ISC). Sempre decorativos: o nome acessível vem do texto ou do aria-label do botão. */
const ICONS: Record<string, IconNode> = {
  'arrow-up-right': ArrowUpRight,
  bell: Bell,
  building: Building2,
  'calendar-clock': CalendarClock,
  'chevron-down': ChevronDown,
  'chevron-right': ChevronRight,
  'circle-alert': CircleAlert,
  'circle-check': CircleCheck,
  help: CircleHelp,
  clock: Clock,
  columns: Columns3,
  download: Download,
  more: Ellipsis,
  signature: FileSignature,
  spreadsheet: FileSpreadsheet,
  tenders: FileText,
  handshake: Handshake,
  hourglass: Hourglass,
  landmark: Landmark,
  dashboard: LayoutDashboard,
  list: List,
  tasks: ListChecks,
  filter: ListFilter,
  logout: LogOut,
  mail: Mail,
  menu: Menu,
  piggy: PiggyBank,
  plus: Plus,
  records: ScrollText,
  search: Search,
  settings: Settings,
  deals: SquareKanban,
  star: Star,
  trend: TrendingUp,
  alert: TriangleAlert,
  imports: Upload,
  wallet: Wallet,
  x: X,
};

@Component({
  selector: 'app-icon',
  template: `
    <svg
      viewBox="0 0 24 24"
      [attr.width]="size()"
      [attr.height]="size()"
      [attr.stroke-width]="stroke()"
      aria-hidden="true"
      focusable="false"
    >
      @for (node of nodes(); track $index) {
        @switch (node[0]) {
          @case ('path') {
            <svg:path [attr.d]="node[1]['d']" />
          }
          @case ('circle') {
            <svg:circle
              [attr.cx]="node[1]['cx']"
              [attr.cy]="node[1]['cy']"
              [attr.r]="node[1]['r']"
            />
          }
          @case ('rect') {
            <svg:rect
              [attr.x]="node[1]['x']"
              [attr.y]="node[1]['y']"
              [attr.width]="node[1]['width']"
              [attr.height]="node[1]['height']"
              [attr.rx]="node[1]['rx']"
              [attr.ry]="node[1]['ry']"
            />
          }
          @case ('line') {
            <svg:line
              [attr.x1]="node[1]['x1']"
              [attr.y1]="node[1]['y1']"
              [attr.x2]="node[1]['x2']"
              [attr.y2]="node[1]['y2']"
            />
          }
          @case ('polyline') {
            <svg:polyline [attr.points]="node[1]['points']" />
          }
          @case ('polygon') {
            <svg:polygon [attr.points]="node[1]['points']" />
          }
          @case ('ellipse') {
            <svg:ellipse
              [attr.cx]="node[1]['cx']"
              [attr.cy]="node[1]['cy']"
              [attr.rx]="node[1]['rx']"
              [attr.ry]="node[1]['ry']"
            />
          }
        }
      }
    </svg>
  `,
  styles: `
    :host {
      display: inline-flex;
      flex: none;
    }
    svg {
      fill: none;
      stroke: currentColor;
      stroke-linecap: round;
      stroke-linejoin: round;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Icon {
  readonly name = input.required<string>();
  readonly size = input(18);
  readonly stroke = input(1.75);
  protected readonly nodes = computed(() => ICONS[this.name()] ?? []);
}
