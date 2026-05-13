#!/bin/bash
#
# Saga Chain Demo Script
# =====================
# This script seeds the database with test data and demonstrates
# the full Saga orchestration flow (create order → payment → inventory → merchant credit).
#
# Prerequisites:
#   - Docker Compose infrastructure running (PostgreSQL + Kafka)
#   - All services started (user-service:8081, merchant-service:8082, order-service:8083)
#
# Usage:
#   bash demo-saga.sh           # Full happy path demo
#   bash demo-saga.sh --setup   # Only create test data (no order)
#   bash demo-saga.sh --clean   # Print curl commands to clean up (recreate infra)
#

set -euo pipefail

# ── Service URLs ──────────────────────────────────────────────────────────
USER_SERVICE="http://localhost:8081"
MERCHANT_SERVICE="http://localhost:8082"
ORDER_SERVICE="http://localhost:8083"

# ── Colors ────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m'

log()  { echo -e "${GREEN}[INFO]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
step() { echo -e "\n${CYAN}━━━ $* ━━━${NC}\n"; }
fail() { echo -e "${RED}[FAIL]${NC} $*" >&2; exit 1; }

# ── Helper: POST with JSON body, extract field from response ──────────────
post() {
    local url="$1" body="$2"
    curl -s -X POST "$url" \
        -H "Content-Type: application/json" \
        -d "$body"
}

get() {
    local url="$1"
    curl -s "$url"
}

extract() {
    # Usage: echo "$json" | extract field
    python3 -c "import json,sys; print(json.load(sys.stdin)['$1'])" 2>/dev/null || \
    jq -r ".$1" 2>/dev/null
}

wait_for_service() {
    local url="$1" name="$2" retries=30
    for i in $(seq 1 $retries); do
        if curl -s -o /dev/null -w '' "$url" 2>/dev/null; then
            log "$name is ready"
            return 0
        fi
        warn "Waiting for $name... ($i/$retries)"
        sleep 2
    done
    fail "$name not reachable at $url after $((retries * 2))s"
}

# ── Parse args ────────────────────────────────────────────────────────────
SETUP_ONLY=false
if [[ "${1:-}" == "--setup" ]]; then SETUP_ONLY=true; fi
if [[ "${1:-}" == "--clean" ]]; then
    echo "To start fresh:"
    echo "  docker compose down -v"
    echo "  docker compose up -d"
    echo "  Then re-run this script."
    exit 0
fi

# ══════════════════════════════════════════════════════════════════════════
# Step 0: Health check
# ══════════════════════════════════════════════════════════════════════════
step "Checking service availability"
wait_for_service "$USER_SERVICE/api/v1/users" "user-service"
wait_for_service "$MERCHANT_SERVICE/api/v1/merchants" "merchant-service"
wait_for_service "$ORDER_SERVICE/api/v1/orders" "order-service"

# ══════════════════════════════════════════════════════════════════════════
# Step 1: Create User Account
# ══════════════════════════════════════════════════════════════════════════
step "Creating user accounts"

USER1=$(post "$USER_SERVICE/api/v1/users" '{"username":"alice"}')
log "User 1: $USER1"
USER_ID=$(echo "$USER1" | extract userId)

USER2=$(post "$USER_SERVICE/api/v1/users" '{"username":"bob"}')
log "User 2: $USER2"
USER2_ID=$(echo "$USER2" | extract userId)

# ══════════════════════════════════════════════════════════════════════════
# Step 2: Top Up User Balance
# ══════════════════════════════════════════════════════════════════════════
step "Topping up user balances"

TOPUP1=$(post "$USER_SERVICE/api/v1/users/$USER_ID/topup" '{"amount": 1000.00}')
log "Alice topped up: $TOPUP1"

TOPUP2=$(post "$USER_SERVICE/api/v1/users/$USER2_ID/topup" '{"amount": 500.00}')
log "Bob topped up: $TOPUP2"

# ══════════════════════════════════════════════════════════════════════════
# Step 3: Create Merchant Account
# ══════════════════════════════════════════════════════════════════════════
step "Creating merchant accounts"

MERCHANT1=$(post "$MERCHANT_SERVICE/api/v1/merchants" '{"name":"TechStore"}')
log "Merchant 1: $MERCHANT1"
MERCHANT_ID=$(echo "$MERCHANT1" | extract merchantId)

MERCHANT2=$(post "$MERCHANT_SERVICE/api/v1/merchants" '{"name":"BookShop"}')
log "Merchant 2: $MERCHANT2"
MERCHANT2_ID=$(echo "$MERCHANT2" | extract merchantId)

# ══════════════════════════════════════════════════════════════════════════
# Step 4: Create Products
# ══════════════════════════════════════════════════════════════════════════
step "Creating products"

PRODUCT1=$(post "$MERCHANT_SERVICE/api/v1/merchants/$MERCHANT_ID/products" '{
  "sku": "LAPTOP-001",
  "name": "MacBook Pro 16",
  "price": 1999.99,
  "quantity": 50
}')
log "Product 1: $PRODUCT1"

PRODUCT2=$(post "$MERCHANT_SERVICE/api/v1/merchants/$MERCHANT_ID/products" '{
  "sku": "MOUSE-001",
  "name": "Wireless Mouse",
  "price": 29.99,
  "quantity": 200
}')
log "Product 2: $PRODUCT2"

PRODUCT3=$(post "$MERCHANT_SERVICE/api/v1/merchants/$MERCHANT2_ID/products" '{
  "sku": "BOOK-001",
  "name": "Designing Data-Intensive Applications",
  "price": 45.99,
  "quantity": 30
}')
log "Product 3: $PRODUCT3"

PRODUCT4=$(post "$MERCHANT_SERVICE/api/v1/merchants/$MERCHANT2_ID/products" '{
  "sku": "BOOK-002",
  "name": "Clean Code",
  "price": 35.50,
  "quantity": 0
}')
log "Product 4 (out of stock): $PRODUCT4"

# ══════════════════════════════════════════════════════════════════════════
# Step 5: Verify setup data
# ══════════════════════════════════════════════════════════════════════════
step "Verifying test data"

ALICE_BAL=$(get "$USER_SERVICE/api/v1/users/$USER_ID/balance")
log "Alice balance: $ALICE_BAL"

BOB_BAL=$(get "$USER_SERVICE/api/v1/users/$USER2_ID/balance")
log "Bob balance: $BOB_BAL"

MERCHANT_BAL=$(get "$MERCHANT_SERVICE/api/v1/merchants/$MERCHANT_ID/balance")
log "TechStore balance: $MERCHANT_BAL"

PRODUCTS=$(get "$MERCHANT_SERVICE/api/v1/merchants/$MERCHANT_ID/products")
log "TechStore products: $PRODUCTS"

if [[ "$SETUP_ONLY" == "true" ]]; then
    echo ""
    log "Setup complete! Summary:"
    echo ""
    echo "  USER_ID      = $USER_ID (alice, balance 1000.00)"
    echo "  USER2_ID     = $USER2_ID (bob, balance 500.00)"
    echo "  MERCHANT_ID  = $MERCHANT_ID (TechStore)"
    echo "  MERCHANT2_ID = $MERCHANT2_ID (BookShop)"
    echo ""
    echo "  Products:"
    echo "    LAPTOP-001  @ 1999.99  qty:50  (TechStore)"
    echo "    MOUSE-001   @ 29.99    qty:200 (TechStore)"
    echo "    BOOK-001    @ 45.99    qty:30  (BookShop)"
    echo "    BOOK-002    @ 35.50    qty:0   (BookShop, out of stock)"
    echo ""
    echo "  To create an order:"
    echo "    curl -s -X POST $ORDER_SERVICE/api/v1/orders \\"
    echo "      -H 'Content-Type: application/json' \\"
    echo "      -d '{\"userId\":\"$USER_ID\",\"merchantId\":\"$MERCHANT_ID\",\"sku\":\"MOUSE-001\",\"quantity\":3}'"
    exit 0
fi

# ══════════════════════════════════════════════════════════════════════════
# Step 6: Demo — Happy Path Order (Saga succeeds)
# ══════════════════════════════════════════════════════════════════════════
step "Demo 1: Happy path — Alice buys 3 wireless mice (3 × 29.99 = 89.97)"

ORDER1=$(post "$ORDER_SERVICE/api/v1/orders" "{
  \"userId\": \"$USER_ID\",
  \"merchantId\": \"$MERCHANT_ID\",
  \"sku\": \"MOUSE-001\",
  \"quantity\": 3
}")
log "Order created: $ORDER1"
ORDER1_ID=$(echo "$ORDER1" | extract orderId)

# Wait for saga to complete (outbox poller runs every 500ms, kafka round-trip ~2s)
log "Waiting for saga to complete..."
sleep 5

ORDER1_STATUS=$(get "$ORDER_SERVICE/api/v1/orders/$ORDER1_ID" | extract status)
log "Order status: $ORDER1_STATUS"

if [[ "$ORDER1_STATUS" == "COMPLETED" ]]; then
    log "Saga completed successfully!"
else
    warn "Order status is $ORDER1_STATUS (may still be processing, check again)"
fi

# Check balances after order
ALICE_BAL_AFTER=$(get "$USER_SERVICE/api/v1/users/$USER_ID/balance")
log "Alice balance after: $ALICE_BAL_AFTER"

MERCHANT_BAL_AFTER=$(get "$MERCHANT_SERVICE/api/v1/merchants/$MERCHANT_ID/balance")
log "TechStore balance after: $MERCHANT_BAL_AFTER"

# ══════════════════════════════════════════════════════════════════════════
# Step 7: Demo — Insufficient Balance (Saga fails at step 1)
# ══════════════════════════════════════════════════════════════════════════
step "Demo 2: Payment failure — Bob tries to buy a laptop (500 < 1999.99)"

ORDER2=$(post "$ORDER_SERVICE/api/v1/orders" "{
  \"userId\": \"$USER2_ID\",
  \"merchantId\": \"$MERCHANT_ID\",
  \"sku\": \"LAPTOP-001\",
  \"quantity\": 1
}")
log "Order created: $ORDER2"
ORDER2_ID=$(echo "$ORDER2" | extract orderId)

log "Waiting for saga..."
sleep 5

ORDER2_STATUS=$(get "$ORDER_SERVICE/api/v1/orders/$ORDER2_ID" | extract status)
log "Order status: $ORDER2_STATUS"

BOB_BAL_AFTER=$(get "$USER_SERVICE/api/v1/users/$USER2_ID/balance")
log "Bob balance after (should be unchanged): $BOB_BAL_AFTER"

# ══════════════════════════════════════════════════════════════════════════
# Step 8: Demo — Insufficient Inventory (Saga fails at step 2, triggers refund)
# ══════════════════════════════════════════════════════════════════════════
step "Demo 3: Inventory failure — Alice buys an out-of-stock book"

ORDER3=$(post "$ORDER_SERVICE/api/v1/orders" "{
  \"userId\": \"$USER_ID\",
  \"merchantId\": \"$MERCHANT2_ID\",
  \"sku\": \"BOOK-002\",
  \"quantity\": 1
}")
log "Order created: $ORDER3"
ORDER3_ID=$(echo "$ORDER3" | extract orderId)

log "Waiting for saga (payment deduct → inventory fail → refund)..."
sleep 5

ORDER3_STATUS=$(get "$ORDER_SERVICE/api/v1/orders/$ORDER3_ID" | extract status)
log "Order status: $ORDER3_STATUS"

ALICE_BAL_FINAL=$(get "$USER_SERVICE/api/v1/users/$USER_ID/balance")
log "Alice balance after (should be same as before this order): $ALICE_BAL_FINAL"

# ══════════════════════════════════════════════════════════════════════════
# Summary
# ══════════════════════════════════════════════════════════════════════════
step "Demo Summary"

echo ""
echo "  Test Data Created:"
echo "  ┌──────────────────────────────────────────────────────────────┐"
echo "  │ User:      alice ($USER_ID)                    │"
echo "  │ User:      bob   ($USER2_ID)                     │"
echo "  │ Merchant:  TechStore ($MERCHANT_ID)       │"
echo "  │ Merchant:  BookShop  ($MERCHANT2_ID)        │"
echo "  └──────────────────────────────────────────────────────────────┘"
echo ""
echo "  Saga Demos:"
echo "  ┌──────────────────────────────────────────────────────────────┐"
echo "  │ 1. Happy path:    Alice → 3× Mouse    → COMPLETED           │"
echo "  │ 2. Payment fail:  Bob   → 1× Laptop   → FAILED              │"
echo "  │ 3. Inventory fail:Alice → 1× Book(OOS)→ FAILED + refund     │"
echo "  └──────────────────────────────────────────────────────────────┘"
echo ""
echo "  Useful queries:"
echo "    # Alice's orders"
echo "    curl -s $ORDER_SERVICE/api/v1/users/$USER_ID/orders | python3 -m json.tool"
echo ""
echo "    # Bob's orders"
echo "    curl -s $ORDER_SERVICE/api/v1/users/$USER2_ID/orders | python3 -m json.tool"
echo ""
echo "    # Kafka UI: http://localhost:9000"
echo ""
