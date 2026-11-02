'use client';

import { useEffect, useState } from 'react';
import { mediaUrl, type Showcase } from '@/lib/api';
import { toPersianDigits as toFa } from '@/lib/validation';
import { t } from '@/lib/i18n';

type Shot = { src: string; thumb: string; alt: string; caption?: string };

/** Noobatyar mark laid over the picture with CSS at display time; the file
 *  itself is never touched. aria-hidden so it adds nothing to the alt text. */
function Watermark() {
  return (
    // eslint-disable-next-line @next/next/no-img-element -- tiny static brand mark
    <img src="/icon.svg" alt="" aria-hidden="true" className="wm-logo" />
  );
}

function Lightbox({ shots, index, onClose, onIndex }: {
  shots: Shot[]; index: number; onClose: () => void; onIndex: (i: number) => void;
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
      if (e.key === 'ArrowLeft') onIndex(Math.min(shots.length - 1, index + 1));
      if (e.key === 'ArrowRight') onIndex(Math.max(0, index - 1));
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [index, shots.length, onClose, onIndex]);
  const s = shots[index];
  return (
    <div className="lightbox" role="dialog" aria-modal="true" onClick={onClose} data-testid="lightbox">
      <div className="lightbox-body" onClick={(e) => e.stopPropagation()}>
        <div className="wm-wrap">
          {/* eslint-disable-next-line @next/next/no-img-element -- remote media host */}
          <img src={s.src} alt={s.alt} className="lightbox-img" />
          <Watermark />
        </div>
        {s.caption && <p className="lightbox-caption">{s.caption}</p>}
        <button type="button" className="lightbox-close" onClick={onClose} aria-label={t('closeViewer')}>×</button>
      </div>
    </div>
  );
}

export default function ShowcaseSection({ showcase }: { showcase: Showcase }) {
  const [open, setOpen] = useState<{ list: Shot[]; i: number } | null>(null);
  const portfolio: Shot[] = showcase.portfolio.map((p) => ({
    src: mediaUrl(p.image) ?? '', thumb: mediaUrl(p.thumbnail || p.image) ?? '',
    alt: p.caption || t('portfolio'), caption: p.caption,
  }));
  const certs: Shot[] = showcase.certificates.map((c) => ({
    src: mediaUrl(c.image) ?? '', thumb: mediaUrl(c.thumbnail || c.image) ?? '',
    alt: c.title, caption: [c.title, c.issuer, c.year ? toFa(String(c.year)) : ''].filter(Boolean).join(' - '),
  }));

  const grid = (list: Shot[], kind: string) => (
    <div className={`showcase-grid ${kind}`}>
      {list.map((s, i) => (
        <button key={i} type="button" className="wm-wrap showcase-item" onClick={() => setOpen({ list, i })} data-testid={`${kind}-item`}>
          {/* eslint-disable-next-line @next/next/no-img-element -- remote media host */}
          <img src={s.thumb} alt={s.alt} loading="lazy" />
          <Watermark />
        </button>
      ))}
    </div>
  );

  return (
    <>
      {portfolio.length > 0 && (
        <section className="section" style={{ paddingTop: 8, paddingBottom: 8 }} data-testid="portfolio-section">
          <h2 className="section-title">{t('portfolio')}</h2>
          {grid(portfolio, 'portfolio')}
        </section>
      )}
      {certs.length > 0 && (
        <section className="section" style={{ paddingTop: 8, paddingBottom: 8 }} data-testid="certificates-section">
          <h2 className="section-title">{t('certificates')}</h2>
          {grid(certs, 'certificates')}
        </section>
      )}
      {open && (
        <Lightbox shots={open.list} index={open.i} onClose={() => setOpen(null)}
          onIndex={(i) => setOpen({ list: open.list, i })} />
      )}
    </>
  );
}
