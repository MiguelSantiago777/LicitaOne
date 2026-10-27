import { describe, expect, it } from 'vitest';
import { TenderResponseStatus } from '../../api/generated/model';
import { ACTION_LABELS, TENDER_TABS, statusTone } from './tender-meta';

describe('tender-meta', () => {
  it('toda situação aparece em exatamente uma aba', () => {
    for (const status of Object.values(TenderResponseStatus)) {
      expect(TENDER_TABS.filter((t) => t.statuses.includes(status))).toHaveLength(1);
    }
  });

  it('toda situação tem cor e rótulo de ação', () => {
    for (const status of Object.values(TenderResponseStatus)) {
      expect(typeof statusTone(status)).toBe('string');
      expect(ACTION_LABELS[status]).toBeTruthy();
    }
  });
});
