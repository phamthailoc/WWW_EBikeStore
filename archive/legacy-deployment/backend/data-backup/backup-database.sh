#!/bin/bash

BACKUP_DIR="./data-backup"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
DB_NAME="ebike_db"
DB_USER="ebike_user"

# Create backup directory
mkdir -p $BACKUP_DIR

# Backup PostgreSQL
docker exec ebike-postgres pg_dump \
    -U $DB_USER \
    -d $DB_NAME \
    > $BACKUP_DIR/ebike_db_$TIMESTAMP.sql

# Backup Redis
docker exec ebike-redis redis-cli \
    -a redis_password_123 \
    BGSAVE

# Compress backups
gzip $BACKUP_DIR/ebike_db_$TIMESTAMP.sql

echo "Backup completed: $BACKUP_DIR/ebike_db_$TIMESTAMP.sql.gz"