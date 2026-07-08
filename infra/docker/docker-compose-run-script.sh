#!/bin/bash

# Change to the script's directory so all relative paths work correctly
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# ---------------------------------------------------------------------------
# 1. Check for environment file (.env, .env.dev, .env.test, or .env.prod)
# ---------------------------------------------------------------------------
echo "🔍 Checking for environment file..."
ENV_FOUND=""

for file in ".env.dev" ".env.test" ".env.prod"; do
    if [ -f "$file" ]; then
        ENV_FOUND="$file"
        echo "✅ Found environment file: $file"
        break
    fi
done

if [ -z "$ENV_FOUND" ]; then
    echo "❌ ERROR: No environment file found!"
    echo "   Please create one of the following in $SCRIPT_DIR:"
    echo "     - .env.dev"
    echo "     - .env.test"
    echo "     - .env.prod"
    exit 1
fi

# ---------------------------------------------------------------------------
# 2. Check for realm_config.json
# ---------------------------------------------------------------------------
echo "🔍 Checking for realm configuration file..."
REALM_FILE="$SCRIPT_DIR/../keycloak/realm_config.json"

if [ ! -f "$REALM_FILE" ]; then
    echo "❌ ERROR: Required realm configuration file not found!"
    echo "   Expected path: $REALM_FILE"
    echo "   Please create this file before starting Keycloak."
    exit 1
fi
echo "✅ Realm file found."

# ---------------------------------------------------------------------------
# (Optional but recommended) Validate that the compose files exist
# ---------------------------------------------------------------------------
if [ ! -f "docker-compose.base.yaml" ]; then
    echo "❌ ERROR: docker-compose.base.yaml not found in $SCRIPT_DIR"
    exit 1
fi

if [ ! -f "docker-compose.dev.yaml" ]; then
    echo "❌ ERROR: docker-compose.dev.yaml not found in $SCRIPT_DIR"
    exit 1
fi

# ---------------------------------------------------------------------------
# 3. Run docker compose with the found environment file
# ---------------------------------------------------------------------------
echo "🚀 Starting services with $ENV_FOUND..."
docker compose \
    --env-file "$ENV_FOUND" \
    -f docker-compose.base.yaml \
    -f docker-compose.dev.yaml \
    up --build "$@"