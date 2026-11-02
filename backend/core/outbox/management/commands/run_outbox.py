"""Long-running outbox worker. ``--once`` drains what is due and exits."""

import logging
import signal
import threading
import time

from django.core.management.base import BaseCommand
from django.db import close_old_connections, connections

from core.outbox import service

logger = logging.getLogger(__name__)

IDLE_RECONNECT_EVERY = 20


class Command(BaseCommand):
    help = 'Process the DB outbox (notifications, bulk sends).'

    def add_arguments(self, parser):
        parser.add_argument('--once', action='store_true',
                            help='Process everything currently due, then exit.')
        parser.add_argument('--batch-size', type=int, default=20)
        parser.add_argument('--poll-interval', type=float, default=3.0)
        parser.add_argument('--lane', choices=['default', 'bulk', 'all'], default='all',
                            help="'bulk' = campaign kinds, 'default' = everything else.")
        parser.add_argument('--max-attempts', type=int, default=service.MAX_ATTEMPTS)

    def handle(self, *args, **opts):
        stop = threading.Event()

        def _stop(signum, frame):
            logger.info('run_outbox: signal %s, finishing current batch', signum)
            stop.set()

        if threading.current_thread() is threading.main_thread():
            signal.signal(signal.SIGTERM, _stop)
            signal.signal(signal.SIGINT, _stop)

        batch_size, max_attempts, lane = opts['batch_size'], opts['max_attempts'], opts['lane']

        if opts['once']:
            service.recover_stuck()
            total = 0
            while True:
                n = service.run_batch(batch_size, max_attempts, lane)
                total += n
                if n == 0:
                    break
            self.stdout.write(f'processed {total}')
            return

        last_recover = 0.0
        idle_polls = 0
        while not stop.is_set():
            handled = 0
            failed = False
            try:
                if time.monotonic() - last_recover > 60:
                    service.recover_stuck()
                    last_recover = time.monotonic()
                handled = service.run_batch(batch_size, max_attempts, lane)
            except Exception:  # noqa: BLE001 — DB blip must not kill the worker
                failed = True
                logger.exception('run_outbox: batch failed')
            finally:
                # Under pgbouncer a stale connection is the usual failure mode.
                close_old_connections()
                idle_polls = idle_polls + 1 if (handled == 0 and not failed) else 0
                # A fresh pgbouncer connection per idle poll is wasteful: drop
                # connections after real work, on error, or every N idle polls.
                if handled or failed or idle_polls >= IDLE_RECONNECT_EVERY:
                    connections.close_all()
                    idle_polls = 0
            if handled == 0:
                stop.wait(opts['poll_interval'])
        logger.info('run_outbox: stopped')
