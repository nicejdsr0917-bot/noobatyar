#!/bin/sh

if [ "$POSTGRES_DB" = "postgres" ] || [ -n "$POSTGRES_DB" ]
then
    echo "Waiting for postgres..."

    while ! nc -z $POSTGRES_HOST $POSTGRES_PORT; do
      sleep 0.1
    done

    echo "PostgreSQL started"
fi

# Only the web service migrates and collects static. Other services built from
# this image (the outbox worker) set SKIP_MIGRATE=1, so two containers never
# run migrate at the same time.
if [ "$SKIP_MIGRATE" != "1" ]; then
    # Run migrations
    echo "Running migrations..."
    python manage.py migrate --noinput

    # Collect static files
    echo "Collecting static files..."
    python manage.py collectstatic --noinput
fi

exec "$@"
