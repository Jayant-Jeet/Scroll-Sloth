#!/usr/bin/env bash
# Run signing on an offline machine. Keep the private key outside this repository.
#   bash tools/sign-rules.sh /absolute/path/outside/repo/rules-private.pem
# CI and local verification (no private key required):
#   bash tools/sign-rules.sh --verify
set -euo pipefail
cd "$(dirname "$0")/.."

rules=site/detection-rules.json
signature="$rules.sig"

verify() {
  test -s "$signature" && test "$(wc -c < "$signature")" -le 1024 || {
    echo "Signature check failed: missing or oversized $signature" >&2
    return 1
  }
  local verified=false found=false
  local slot pem der
  for slot in primary backup; do
    pem="site/keys/rules-pubkey-$slot.pem"
    der="app/src/main/assets/rules-pubkey-$slot.der"
    if [[ ! -f "$pem" && ! -f "$der" ]]; then continue; fi
    found=true
    test -s "$pem" && test -s "$der" || {
      echo "Both PEM and DER public keys are required for $slot." >&2
      return 1
    }
    openssl pkey -pubin -in "$pem" -text -noout | grep -q 'ASN1 OID: prime256v1' || {
      echo "Expected an ECDSA P-256 public key for $slot." >&2
      return 1
    }
    cmp <(openssl pkey -pubin -in "$pem" -outform DER) "$der" || {
      echo "PEM and embedded DER public keys differ for $slot." >&2
      return 1
    }
    if openssl dgst -sha256 -verify "$pem" -signature "$signature" "$rules"; then
      verified=true
    fi
  done
  if [[ "$found" != true || "$verified" != true ]]; then
    echo "Signature check failed: no trusted public key verified the rules." >&2
    return 1
  fi
}

if [[ "${1:-}" == --verify && $# == 1 ]]; then
  verify
elif [[ $# == 1 && -f "$1" ]]; then
  # The private key is read only by OpenSSL; it is never copied or printed.
  openssl dgst -sha256 -sign "$1" -out "$signature" "$rules"
  verify
else
  echo "Usage: bash tools/sign-rules.sh --verify | /path/outside/repo/private-key.pem" >&2
  exit 2
fi
