#!/usr/bin/env bash

set -Eeuo pipefail

readonly CONTAINER_NAME="ersync-api"
readonly PREVIOUS_CONTAINER_NAME="${CONTAINER_NAME}-previous"
readonly HOST_PORT="8080"
readonly CONTAINER_PORT="8080"
readonly HEALTH_URL="http://127.0.0.1:${HOST_PORT}/actuator/health/readiness"
readonly SECRET_ID="ersync/dev/backend"
readonly SECRET_RUNTIME_DIRECTORY="/run/ersync"
readonly SECRET_CONTAINER_PATH="/app/config/application.yaml"
readonly SECRET_POINTER_DIRECTORY="/etc/ersync"
readonly SECRET_POINTER_FILE="${SECRET_POINTER_DIRECTORY}/current-secret-path"
readonly SECRET_REFRESH_SERVICE="/etc/systemd/system/ersync-secret-refresh.service"
readonly CONTAINER_RECOVERY_SERVICE="/etc/systemd/system/ersync-container-recovery.service"
readonly DOCKER_DROP_IN_DIRECTORY="/etc/systemd/system/docker.service.d"
readonly DOCKER_SECRET_DEPENDENCY="${DOCKER_DROP_IN_DIRECTORY}/ersync-secret-refresh.conf"
readonly APP_UID="10001"
readonly APP_GID="10001"

IMAGE_URI=""
AWS_REGION=""
IMAGE_TAG=""
ECR_REGISTRY=""
previous_image=""
previous_secret_file=""
new_secret_file=""
temporary_secret_file=""
has_previous_container=false
deployment_in_progress=false
deployment_succeeded=false

log() {
    printf '[ersync-deploy] %s\n' "$*"
}

require_root() {
    if [[ "${EUID}" -ne 0 ]]; then
        log "Deployment must run as root."
        exit 2
    fi
}

require_commands() {
    local command_name

    for command_name in "$@"; do
        if ! command -v "${command_name}" >/dev/null 2>&1; then
            log "Required command is missing: ${command_name}"
            exit 2
        fi
    done
}

container_exists() {
    docker container inspect "$1" >/dev/null 2>&1
}

secret_mount_source() {
    docker inspect \
        --format '{{range .Mounts}}{{if eq .Destination "/app/config/application.yaml"}}{{.Source}}{{end}}{{end}}' \
        "$1" 2>/dev/null || true
}

remove_secret_file() {
    local secret_file="$1"

    case "${secret_file}" in
        "${SECRET_RUNTIME_DIRECTORY}"/application-*.yaml)
            rm -f -- "${secret_file}"
            ;;
    esac
}

wait_for_readiness() {
    local container_name="$1"
    local attempt

    for attempt in $(seq 1 45); do
        if curl --fail --silent --max-time 3 \
            --output /dev/null "${HEALTH_URL}"; then
            return 0
        fi

        if ! container_exists "${container_name}"; then
            return 1
        fi

        if [[ "$(docker inspect --format '{{.State.Running}}' "${container_name}")" != "true" ]]; then
            return 1
        fi

        sleep 2
    done

    return 1
}

write_secret_pointer() {
    local secret_file="$1"
    local pointer_temp

    install -d -m 0755 -o root -g root "${SECRET_POINTER_DIRECTORY}"
    pointer_temp="$(mktemp "${SECRET_POINTER_DIRECTORY}/.current-secret-path.XXXXXX")"
    printf '%s\n' "${secret_file}" > "${pointer_temp}"
    chmod 0644 "${pointer_temp}"
    mv -f "${pointer_temp}" "${SECRET_POINTER_FILE}"
}

write_secret_config() {
    local destination="$1"
    local secret_json=""
    local attempt

    install -d -m 0700 -o root -g root "${SECRET_RUNTIME_DIRECTORY}"
    temporary_secret_file="$(mktemp "${SECRET_RUNTIME_DIRECTORY}/.application-secret.XXXXXX")"

    for attempt in $(seq 1 5); do
        if secret_json="$(
            aws secretsmanager get-secret-value \
                --secret-id "${SECRET_ID}" \
                --region "${AWS_REGION}" \
                --query SecretString \
                --output text \
                --cli-connect-timeout 5 \
                --cli-read-timeout 10
        )"; then
            break
        fi

        if [[ "${attempt}" -eq 5 ]]; then
            log "Unable to retrieve the application Secret."
            rm -f "${temporary_secret_file}"
            temporary_secret_file=""
            return 1
        fi

        sleep $((attempt * 2))
    done

    if ! jq --exit-status --raw-output '
        def required_string($key):
            .[$key]
            | if type == "string" and length > 0
              then .
              else error("missing or invalid key: " + $key)
              end;

        . as $secret
        | required_string("engine") as $engine
        | required_string("host") as $host
        | (.port | tostring) as $port
        | required_string("dbname") as $dbname
        | required_string("username") as $username
        | required_string("password") as $password
        | if $engine != "mysql" then error("engine must be mysql") else . end
        | if ($host | test("^[A-Za-z0-9.-]+$") | not)
          then error("invalid database host")
          else .
          end
        | if ($port | test("^[0-9]+$") | not)
          then error("invalid database port")
          else .
          end
        | if ($dbname | test("^[A-Za-z0-9_]+$") | not)
          then error("invalid database name")
          else .
          end
        | (
            "jdbc:mysql://"
            + $host
            + ":"
            + $port
            + "/"
            + $dbname
            + "?sslMode=VERIFY_IDENTITY"
            + "&connectionTimeZone=UTC"
            + "&forceConnectionTimeZoneToSession=true"
          ) as $jdbc_url
        | [
            "spring:",
            "  datasource:",
            ("    url: " + ($jdbc_url | @json)),
            ("    username: " + ($username | @json)),
            ("    password: " + ($password | @json))
          ]
        | .[]
    ' <<< "${secret_json}" > "${temporary_secret_file}"; then
        log "The application Secret does not match the required schema."
        rm -f "${temporary_secret_file}"
        temporary_secret_file=""
        secret_json=""
        return 1
    fi

    secret_json=""
    chown "${APP_UID}:${APP_GID}" "${temporary_secret_file}"
    chmod 0400 "${temporary_secret_file}"
    mv -f "${temporary_secret_file}" "${destination}"
    temporary_secret_file=""
}

install_secret_refresh_service() {
    local dependency_temp
    local service_temp

    service_temp="$(mktemp /tmp/ersync-secret-refresh.service.XXXXXX)"
    cat > "${service_temp}" <<EOF
[Unit]
Description=Prepare ERSync runtime Secret before Docker starts
Wants=network-online.target
After=network-online.target
Before=docker.service
PartOf=docker.service
OnSuccess=ersync-container-recovery.service
StartLimitIntervalSec=0

[Service]
Type=oneshot
ExecStart=/usr/local/bin/ersync-deploy --prepare-secret ${AWS_REGION}
Restart=on-failure
RestartSec=15

[Install]
WantedBy=multi-user.target
EOF

    chmod 0644 "${service_temp}"
    if [[ ! -f "${SECRET_REFRESH_SERVICE}" ]] \
        || ! cmp --silent "${service_temp}" "${SECRET_REFRESH_SERVICE}"; then
        mv -f "${service_temp}" "${SECRET_REFRESH_SERVICE}"
        systemctl daemon-reload
    else
        rm -f "${service_temp}"
    fi

    install -d -m 0755 -o root -g root "${DOCKER_DROP_IN_DIRECTORY}"
    dependency_temp="$(mktemp /tmp/ersync-docker-secret-dependency.XXXXXX)"
    cat > "${dependency_temp}" <<EOF
[Unit]
Requires=ersync-secret-refresh.service
After=ersync-secret-refresh.service
EOF
    chmod 0644 "${dependency_temp}"

    if [[ ! -f "${DOCKER_SECRET_DEPENDENCY}" ]] \
        || ! cmp --silent "${dependency_temp}" "${DOCKER_SECRET_DEPENDENCY}"; then
        mv -f "${dependency_temp}" "${DOCKER_SECRET_DEPENDENCY}"
        systemctl daemon-reload
    else
        rm -f "${dependency_temp}"
    fi

    systemctl enable ersync-secret-refresh.service >/dev/null
}

install_container_recovery_service() {
    local service_temp

    service_temp="$(mktemp /tmp/ersync-container-recovery.service.XXXXXX)"
    cat > "${service_temp}" <<EOF
[Unit]
Description=Recover an interrupted ERSync container deployment
Requires=docker.service
After=docker.service
PartOf=docker.service
StartLimitIntervalSec=0

[Service]
Type=oneshot
ExecStart=/usr/local/bin/ersync-deploy --recover-container
RemainAfterExit=yes
Restart=on-failure
RestartSec=15

[Install]
WantedBy=multi-user.target
EOF

    chmod 0644 "${service_temp}"
    if [[ ! -f "${CONTAINER_RECOVERY_SERVICE}" ]] \
        || ! cmp --silent "${service_temp}" "${CONTAINER_RECOVERY_SERVICE}"; then
        mv -f "${service_temp}" "${CONTAINER_RECOVERY_SERVICE}"
        systemctl daemon-reload
    else
        rm -f "${service_temp}"
    fi

    systemctl enable ersync-container-recovery.service >/dev/null
}

prepare_secret_after_reboot() {
    local pointer_value

    AWS_REGION="${1:?Usage: deploy-ec2.sh --prepare-secret <aws-region>}"
    require_root
    require_commands aws jq install

    if [[ ! "${AWS_REGION}" =~ ^[a-z]{2}-[a-z]+-[0-9]+$ ]]; then
        log "Invalid AWS region."
        exit 2
    fi

    if [[ ! -f "${SECRET_POINTER_FILE}" ]]; then
        log "No active Secret file is registered."
        return 1
    fi

    pointer_value="$(< "${SECRET_POINTER_FILE}")"
    case "${pointer_value}" in
        "${SECRET_RUNTIME_DIRECTORY}"/application-*.yaml)
            ;;
        *)
            log "The registered Secret path is invalid."
            exit 2
            ;;
    esac

    write_secret_config "${pointer_value}"
    log "Runtime Secret prepared."
}

recover_container_after_reboot() {
    require_root
    require_commands curl docker install

    prepare_existing_container
    if container_exists "${CONTAINER_NAME}" && wait_for_readiness "${CONTAINER_NAME}"; then
        log "Active container is ready."
        return 0
    fi

    log "No ready container could be recovered."
    return 1
}

restore_previous_container() {
    local failed_secret_file=""
    local replacement_secret_file=""

    log "New container failed readiness. Starting rollback."
    if container_exists "${CONTAINER_NAME}"; then
        failed_secret_file="$(secret_mount_source "${CONTAINER_NAME}")"
        docker rm --force "${CONTAINER_NAME}" >/dev/null 2>&1 || true
    fi

    if [[ "${has_previous_container}" != "true" ]] || ! container_exists "${PREVIOUS_CONTAINER_NAME}"; then
        remove_secret_file "${failed_secret_file}"
        remove_secret_file "${new_secret_file}"
        log "No previous container is available for rollback."
        return 1
    fi

    replacement_secret_file="${failed_secret_file:-${new_secret_file}}"
    if [[ -n "${previous_secret_file}" && -f "${replacement_secret_file}" ]]; then
        if ! install \
            -m 0400 \
            -o "${APP_UID}" \
            -g "${APP_GID}" \
            "${replacement_secret_file}" \
            "${previous_secret_file}"; then
            log "Unable to refresh the rollback container Secret."
        fi
    fi

    remove_secret_file "${failed_secret_file}"
    remove_secret_file "${new_secret_file}"
    docker rename "${PREVIOUS_CONTAINER_NAME}" "${CONTAINER_NAME}"
    docker start "${CONTAINER_NAME}" >/dev/null

    if [[ -n "${previous_secret_file}" ]]; then
        write_secret_pointer "${previous_secret_file}"
    fi

    if wait_for_readiness "${CONTAINER_NAME}"; then
        log "Rollback completed."
        return 0
    fi

    log "Previous container was restarted but did not become ready."
    return 1
}

cleanup() {
    local exit_code=$?

    set +e

    if [[ -n "${temporary_secret_file}" ]]; then
        rm -f "${temporary_secret_file}"
    fi

    if [[ "${deployment_in_progress}" == "true" && "${deployment_succeeded}" != "true" ]]; then
        restore_previous_container
    elif [[ "${deployment_succeeded}" != "true" && -n "${new_secret_file}" ]]; then
        remove_secret_file "${new_secret_file}"
    fi

    if [[ -n "${ECR_REGISTRY}" ]]; then
        docker logout "${ECR_REGISTRY}" >/dev/null 2>&1 || true
    fi

    exit "${exit_code}"
}

validate_input() {
    if [[ "${IMAGE_URI}" != *.dkr.ecr."${AWS_REGION}".amazonaws.com/*:* ]]; then
        log "Image URI does not match the configured AWS region."
        exit 2
    fi

    if [[ ! "${AWS_REGION}" =~ ^[a-z]{2}-[a-z]+-[0-9]+$ ]]; then
        log "Invalid AWS region."
        exit 2
    fi

    if [[ ! "${IMAGE_TAG}" =~ ^[0-9a-f]{40}$ ]]; then
        log "Deployment image tag must be a full Git commit SHA."
        exit 2
    fi
}

prepare_existing_container() {
    local current_secret_file=""
    local stale_secret_file=""

    if ! container_exists "${PREVIOUS_CONTAINER_NAME}"; then
        return
    fi

    stale_secret_file="$(secret_mount_source "${PREVIOUS_CONTAINER_NAME}")"
    if container_exists "${CONTAINER_NAME}"; then
        current_secret_file="$(secret_mount_source "${CONTAINER_NAME}")"
        if wait_for_readiness "${CONTAINER_NAME}"; then
            log "Removing a stale rollback container."
            if ! docker rm --force "${PREVIOUS_CONTAINER_NAME}" >/dev/null; then
                log "Unable to remove the stale rollback container."
                return 1
            fi
            if [[ "${stale_secret_file}" != "${current_secret_file}" ]]; then
                remove_secret_file "${stale_secret_file}"
            fi
            if [[ -n "${current_secret_file}" ]]; then
                write_secret_pointer "${current_secret_file}"
            fi
        else
            log "Recovering from an interrupted deployment."
            if ! docker rm --force "${CONTAINER_NAME}" >/dev/null; then
                log "Unable to remove the interrupted container."
                return 1
            fi
            remove_secret_file "${current_secret_file}"
            if ! docker rename "${PREVIOUS_CONTAINER_NAME}" "${CONTAINER_NAME}"; then
                log "Unable to restore the rollback container name."
                return 1
            fi
            if ! docker start "${CONTAINER_NAME}" >/dev/null; then
                docker rename "${CONTAINER_NAME}" "${PREVIOUS_CONTAINER_NAME}" || true
                log "Unable to start the rollback container."
                return 1
            fi
            if [[ -n "${stale_secret_file}" ]]; then
                write_secret_pointer "${stale_secret_file}"
            else
                rm -f "${SECRET_POINTER_FILE}"
            fi
        fi
    else
        log "Recovering a container left by an interrupted deployment."
        if ! docker rename "${PREVIOUS_CONTAINER_NAME}" "${CONTAINER_NAME}"; then
            log "Unable to restore the rollback container name."
            return 1
        fi
        if ! docker start "${CONTAINER_NAME}" >/dev/null; then
            docker rename "${CONTAINER_NAME}" "${PREVIOUS_CONTAINER_NAME}" || true
            log "Unable to start the rollback container."
            return 1
        fi
        if [[ -n "${stale_secret_file}" ]]; then
            write_secret_pointer "${stale_secret_file}"
        else
            rm -f "${SECRET_POINTER_FILE}"
        fi
    fi
}

prepare_new_secret() {
    install -d -m 0700 -o root -g root "${SECRET_RUNTIME_DIRECTORY}"
    new_secret_file="$(
        mktemp "${SECRET_RUNTIME_DIRECTORY}/application-${IMAGE_TAG}.XXXXXX.yaml"
    )"
    log "Preparing runtime configuration from Secrets Manager."
    if ! write_secret_config "${new_secret_file}"; then
        rm -f "${new_secret_file}"
        new_secret_file=""
        return 1
    fi
}

switch_container() {
    if container_exists "${CONTAINER_NAME}"; then
        previous_image="$(docker inspect --format '{{.Config.Image}}' "${CONTAINER_NAME}")"
        previous_secret_file="$(secret_mount_source "${CONTAINER_NAME}")"
        log "Stopping the current container."
        docker stop --time 30 "${CONTAINER_NAME}" >/dev/null

        if ! docker rename "${CONTAINER_NAME}" "${PREVIOUS_CONTAINER_NAME}"; then
            docker start "${CONTAINER_NAME}" >/dev/null
            return 1
        fi

        has_previous_container=true
    fi

    deployment_in_progress=true
    log "Starting the new container."
    if ! docker run \
        --detach \
        --name "${CONTAINER_NAME}" \
        --restart unless-stopped \
        --stop-timeout 30 \
        --read-only \
        --cap-drop ALL \
        --publish "127.0.0.1:${HOST_PORT}:${CONTAINER_PORT}" \
        --volume "${new_secret_file}:${SECRET_CONTAINER_PATH}:ro,Z" \
        --tmpfs "/tmp:rw,noexec,nosuid,nodev,size=64m,uid=${APP_UID},gid=${APP_GID}" \
        --security-opt no-new-privileges \
        --log-opt max-size=10m \
        --log-opt max-file=3 \
        "${IMAGE_URI}" >/dev/null; then
        return 1
    fi

    if ! wait_for_readiness "${CONTAINER_NAME}"; then
        docker inspect \
            --format 'container_state={{.State.Status}} exit_code={{.State.ExitCode}}' \
            "${CONTAINER_NAME}" >&2 || true
        return 1
    fi
}

finish_deployment() {
    write_secret_pointer "${new_secret_file}"
    install_secret_refresh_service
    deployment_succeeded=true

    if [[ "${has_previous_container}" == "true" ]]; then
        if ! docker rm "${PREVIOUS_CONTAINER_NAME}" >/dev/null; then
            log "The stale rollback container will be cleaned up on the next deployment."
        else
            if [[ "${previous_secret_file}" != "${new_secret_file}" ]]; then
                remove_secret_file "${previous_secret_file}"
            fi
        fi

        if [[ -n "${previous_image}" && "${previous_image}" != "${IMAGE_URI}" ]]; then
            docker image rm "${previous_image}" >/dev/null 2>&1 || true
        fi
    fi

    log "Deployment completed: ${IMAGE_TAG}"
}

deploy_image() {
    IMAGE_URI="${1:?Usage: deploy-ec2.sh <image-uri> <aws-region>}"
    AWS_REGION="${2:?Usage: deploy-ec2.sh <image-uri> <aws-region>}"
    IMAGE_TAG="${IMAGE_URI##*:}"
    ECR_REGISTRY="${IMAGE_URI%%/*}"

    require_root
    require_commands aws cmp curl docker install jq systemctl
    validate_input

    trap cleanup EXIT

    install_container_recovery_service
    prepare_existing_container

    log "Authenticating to ECR."
    aws ecr get-login-password --region "${AWS_REGION}" \
        | docker login --username AWS --password-stdin "${ECR_REGISTRY}" >/dev/null

    log "Pulling image: ${IMAGE_TAG}"
    docker pull "${IMAGE_URI}" >/dev/null

    prepare_new_secret
    switch_container
    finish_deployment
}

main() {
    if [[ "${1:-}" == "--prepare-secret" ]]; then
        prepare_secret_after_reboot "${2:-}"
        return
    fi

    if [[ "${1:-}" == "--recover-container" ]]; then
        recover_container_after_reboot
        return
    fi

    deploy_image "$@"
}

main "$@"
