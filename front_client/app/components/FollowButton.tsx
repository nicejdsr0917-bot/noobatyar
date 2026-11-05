'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { getFollowState, setFollowing } from '@/lib/api';
import { useVisitorToken } from '@/lib/visitorToken';
import { toPersianDigits as toFa } from '@/lib/validation';
import { t } from '@/lib/i18n';

interface Props {
  businessId: number;
  /** The public count the page was rendered with. */
  initialCount: number;
  loginHref: string;
}

/**
 * Follower count and the follow button on a business page. The count is
 * public; the button reads this visitor's own state once signed in. A guest
 * is sent to sign in and comes back here.
 */
export default function FollowButton({ businessId, initialCount, loginHref }: Props) {
  const token = useVisitorToken();
  const router = useRouter();
  const [count, setCount] = useState(initialCount);
  const [following, setFollowingState] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!token) return;
    let stale = false;
    getFollowState(businessId, token)
      .then((s) => {
        if (stale) return;
        setFollowingState(s.is_following);
        setCount(s.followers_count);
      })
      .catch(() => {});
    return () => { stale = true; };
  }, [businessId, token]);

  async function toggle() {
    if (!token) {
      router.push(loginHref);
      return;
    }
    if (busy) return;
    const next = !following;
    // Shown at once; put back if the server refuses.
    setBusy(true);
    setFollowingState(next);
    setCount((c) => Math.max(0, c + (next ? 1 : -1)));
    try {
      const s = await setFollowing(businessId, next, token);
      setFollowingState(s.is_following);
      setCount(s.followers_count);
    } catch {
      setFollowingState(!next);
      setCount((c) => Math.max(0, c + (next ? -1 : 1)));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="follow-row" data-testid="follow-row">
      <span className="follow-count">{t('followersCount', toFa(String(count)))}</span>
      <button
        type="button"
        className={following ? 'follow-btn on' : 'follow-btn'}
        onClick={toggle}
        disabled={busy}
        aria-pressed={following}
        title={token ? undefined : t('followNeedsLogin')}
      >
        {following ? t('followingNow') : t('follow')}
      </button>
    </div>
  );
}
