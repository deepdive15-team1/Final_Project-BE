#!/usr/bin/env bash
set -euo pipefail

umask 077

credential_dir="${HOME:?}/app/secrets"
credential_path="${credential_dir}/firebase-service-account.json"
temporary_path=""

cleanup_temporary_credential() {
    local exit_status=$?
    trap - EXIT HUP INT TERM QUIT
    if [[ -n "${temporary_path}" && -e "${temporary_path}" ]]; then
        rm -f -- "${temporary_path}"
    fi
    exit "${exit_status}"
}

trap cleanup_temporary_credential EXIT
trap 'exit 1' HUP INT TERM QUIT

[[ -n "${FIREBASE_SERVICE_ACCOUNT_JSON_BASE64:-}" ]]
command -v jq >/dev/null

install -d -m 700 -- "${credential_dir}"
temporary_path="$(mktemp "${credential_dir}/.firebase-service-account.json.XXXXXX")"

printf '%s' "${FIREBASE_SERVICE_ACCOUNT_JSON_BASE64}" | base64 --decode > "${temporary_path}"
jq -e '
    type == "object"
    and .type == "service_account"
    and (.project_id | type == "string" and length > 0)
    and (.client_email | type == "string" and length > 0)
    and (.private_key | type == "string" and length > 0)
' "${temporary_path}" >/dev/null

chmod 600 -- "${temporary_path}"
[[ "$(stat -c '%a' "${temporary_path}")" == "600" ]]
mv -- "${temporary_path}" "${credential_path}"
temporary_path=""
