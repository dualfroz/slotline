#!/usr/bin/env bash
set -euo pipefail

BASE="${BASE_URL:-http://localhost:8080}"
ADMIN_USER="${SLOTLINE_ADMIN_USERNAME:-admin}"
ADMIN_PASS="${SLOTLINE_ADMIN_PASSWORD:-admin12345}"

jsonField() {
    python3 -c "import sys, json; print(json.load(sys.stdin)['$1'])"
}

echo "== slotline smoke test against ${BASE} =="

echo "-- 1. admin login"
adminToken=$(curl -sS -X POST "${BASE}/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_PASS}\"}" | jsonField token)
echo "   admin token acquired"

echo "-- 2. create resource (ADMIN)"
resourceId=$(curl -sS -X POST "${BASE}/api/resources" \
    -H "Authorization: Bearer ${adminToken}" \
    -H 'Content-Type: application/json' \
    -d '{"name":"Court 1","description":"Outdoor tennis court"}' | jsonField id)
echo "   resource id=${resourceId}"

echo "-- 3. register user 'alice' (ignore if already exists)"
curl -sS -o /dev/null -w "   HTTP %{http_code}\n" -X POST "${BASE}/api/auth/register" \
    -H 'Content-Type: application/json' \
    -d '{"username":"alice","password":"password123"}' || true

echo "-- 4. user login"
userToken=$(curl -sS -X POST "${BASE}/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"alice","password":"password123"}' | jsonField token)
echo "   user token acquired"

slot='{"resourceId":'"${resourceId}"',"startTime":"2026-09-01T10:00:00Z","endTime":"2026-09-01T11:00:00Z"}'

echo "-- 5. book slot (expect 201)"
reservationId=$(curl -sS -X POST "${BASE}/api/reservations" \
    -H "Authorization: Bearer ${userToken}" \
    -H 'Content-Type: application/json' \
    -d "${slot}" | jsonField id)
echo "   reservation id=${reservationId}"

echo "-- 6. book the same slot again (expect 409)"
conflictCode=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "${BASE}/api/reservations" \
    -H "Authorization: Bearer ${userToken}" \
    -H 'Content-Type: application/json' \
    -d "${slot}")
echo "   HTTP ${conflictCode}"
if [ "${conflictCode}" != "409" ]; then
    echo "   FAIL: expected 409 conflict" >&2
    exit 1
fi

echo "-- 7. cancel reservation (expect 200)"
curl -sS -o /dev/null -w "   HTTP %{http_code}\n" -X POST \
    "${BASE}/api/reservations/${reservationId}/cancel" \
    -H "Authorization: Bearer ${userToken}"

echo "-- 8. list active reservations for resource"
curl -sS "${BASE}/api/resources/${resourceId}/reservations" \
    -H "Authorization: Bearer ${userToken}"
echo

echo "== smoke test passed =="
