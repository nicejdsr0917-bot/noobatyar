'use client';

import { useRouter } from 'next/navigation';
import Icon, { type IconName } from './Icon';
import { t } from '@/lib/i18n';

export interface ToolbarAction {
  icon: IconName;
  label: string;
  onClick: () => void;
}

interface ToolbarProps {
  title: string;
  /** Extra buttons rendered beside the back button. */
  actions?: ToolbarAction[];
  /** Replaces the default `router.back()`. */
  onBack?: () => void;
  /** Drops the back button entirely (root screens). */
  hideBack?: boolean;
  /**
   * `'back'` (default) draws the arrow and implies "go to the previous
   * screen" — safe wherever that previous screen is unambiguous. `'close'`
   * draws an × and should pair with an explicit `onBack` that navigates
   * somewhere specific: mid-flow screens (payment, a multi-step form) sit on
   * top of history entries from *other* attempts (an earlier booking's own
   * checkout/success pages, still sitting in the tab's back/forward list —
   * `router.replace` swaps only the current entry, not the ones around it),
   * so `router.back()` there can resurface a stale, unrelated screen instead
   * of leaving the flow. See the booking checkout page for exactly this bug.
   */
  backIcon?: 'back' | 'close';
}

/**
 * The shared top bar.
 *
 * Follows the document direction: the back control comes first, so it lands
 * on the reading-start side (right in Persian, left in English). The end slot
 * is intentionally left as a spacer — the floating theme and language toggles
 * are fixed there at z-index 200 and would swallow clicks on anything below.
 */
export default function Toolbar({
  title,
  actions = [],
  onBack,
  hideBack = false,
  backIcon = 'back',
}: ToolbarProps) {
  const router = useRouter();

  return (
    <div className="toolbar">
      <div className="toolbar-start" style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
        {actions.map((action) => (
          <button
            key={action.label}
            className="toolbar-back"
            aria-label={action.label}
            title={action.label}
            onClick={action.onClick}
          >
            <Icon name={action.icon} size={20} />
          </button>
        ))}

        {hideBack ? (
          actions.length === 0 && <div className="toolbar-placeholder" />
        ) : (
          <button
            className="toolbar-back"
            aria-label={backIcon === 'close' ? t('cancel') : t('back')}
            title={backIcon === 'close' ? t('cancel') : t('back')}
            onClick={onBack ?? (() => router.back())}
          >
            <Icon name={backIcon === 'close' ? 'close' : 'back'} size={20} />
          </button>
        )}
      </div>

      <h1 className="toolbar-title">{title}</h1>
      <div className="toolbar-placeholder" />
    </div>
  );
}
