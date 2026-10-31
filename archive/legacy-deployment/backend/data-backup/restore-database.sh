#!/bin/bash

BACKUP_FILE=$1
DB_NAME="ebike_db"
DB_USER="ebike_user"

if [ -z "$BACKUP_FILE" ]; then
    echo "Usage: $0 <backup-file>"
    exit 1
fi

# Restore PostgreSQL
gunzip -c $BACKUP_FILE | docker exec -i ebike-postgres psql \
    -U $DB_USER \
    -d $DB_NAME

echo "Database restored from: $BACKUP_FILE"