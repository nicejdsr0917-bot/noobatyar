'use client';

import { useRouter } from 'next/navigation';
import Icon, { type IconName } from './Icon';

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
 * `.toolbar` is `direction: ltr` on purpose (see globals.css) so the slots stay
 * where the CSS puts them regardless of content; the back control therefore
 * lands on the right, which is the reading-start side in this RTL app. The
 * left slot is intentionally left as a spacer — the floating `.theme-toggle`
 * is fixed there at z-index 200 and would swallow clicks on anything below it.
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
      <div className="toolbar-placeholder" />
      <h1 className="toolbar-title">{title}</h1>

      <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
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
            aria-label={backIcon === 'close' ? 'انصراف' : 'بازگشت'}
            title={backIcon === 'close' ? 'انصراف' : 'بازگشت'}
            onClick={onBack ?? (() => router.back())}
          >
            <Icon name={backIcon === 'close' ? 'close' : 'back'} size={20} />
          </button>
        )}
      </div>
    </div>
  );
}
