#!/usr/bin/env bash

set -Eeuo pipefail

readonly IMAGE_URI="${1:?Usage: deploy-ec2.sh <image-uri> <aws-region>}"
readonly AWS_REGION="${2:?Usage: deploy-ec2.sh <image-uri> <aws-region>}"
readonly CONTAINER_NAME="ersync-api"
readonly PREVIOUS_CONTAINER_NAME="${CONTAINER_NAME}-previous"
readonly HOST_PORT="8080"
readonly CONTAINER_PORT="8080"
readonly HEALTH_URL="http://127.0.0.1:${HOST_PORT}/actuator/health/readiness"
readonly ECR_REGISTRY="${IMAGE_URI%%/*}"

previous_image=""
has_previous_container=false
deployment_in_progress=false
deployment_succeeded=false

log() {
    printf '[ersync-deploy] %s\n' "$*"
}

container_exists() {
    docker container inspect "$1" >/dev/null 2>&1
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

restore_previous_container() {
    log "New container failed readiness. Starting rollback."
    docker rm --force "${CONTAINER_NAME}" >/dev/null 2>&1 || true

    if [[ "${has_previous_container}" != "true" ]] || ! container_exists "${PREVIOUS_CONTAINER_NAME}"; then
        log "No previous container is available for rollback."
        return 1
    fi

    docker rename "${PREVIOUS_CONTAINER_NAME}" "${CONTAINER_NAME}"
    docker start "${CONTAINER_NAME}" >/dev/null

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

    if [[ "${deployment_in_progress}" == "true" && "${deployment_succeeded}" != "true" ]]; then
        restore_previous_container
    fi

    docker logout "${ECR_REGISTRY}" >/dev/null 2>&1 || true
    exit "${exit_code}"
}

validate_input() {
    if [[ "${IMAGE_URI}" != *.dkr.ecr."${AWS_REGION}".amazonaws.com/*:* ]]; then
        log "Image URI does not match the configured AWS region."
        exit 2
    fi

    if [[ "${IMAGE_URI##*:}" == "latest" ]]; then
        log "The latest tag is not allowed for deployments."
        exit 2
    fi
}

prepare_existing_container() {
    if container_exists "${PREVIOUS_CONTAINER_NAME}"; then
        if container_exists "${CONTAINER_NAME}"; then
            if wait_for_readiness "${CONTAINER_NAME}"; then
                log "Removing a stale rollback container."
                docker rm --force "${PREVIOUS_CONTAINER_NAME}" >/dev/null
            else
                log "Recovering from an interrupted deployment."
                docker rm --force "${CONTAINER_NAME}" >/dev/null
                docker rename "${PREVIOUS_CONTAINER_NAME}" "${CONTAINER_NAME}"
                docker start "${CONTAINER_NAME}" >/dev/null
            fi
        else
            log "Recovering a container left by an interrupted deployment."
            docker rename "${PREVIOUS_CONTAINER_NAME}" "${CONTAINER_NAME}"
            docker start "${CONTAINER_NAME}" >/dev/null
        fi
    fi
}

switch_container() {
    if container_exists "${CONTAINER_NAME}"; then
        previous_image="$(docker inspect --format '{{.Config.Image}}' "${CONTAINER_NAME}")"
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
        --publish "127.0.0.1:${HOST_PORT}:${CONTAINER_PORT}" \
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

    deployment_succeeded=true
}

finish_deployment() {
    if [[ "${has_previous_container}" == "true" ]]; then
        if ! docker rm "${PREVIOUS_CONTAINER_NAME}" >/dev/null; then
            log "The stale rollback container will be cleaned up on the next deployment."
        fi

        if [[ -n "${previous_image}" && "${previous_image}" != "${IMAGE_URI}" ]]; then
            docker image rm "${previous_image}" >/dev/null 2>&1 || true
        fi
    fi

    log "Deployment completed: ${IMAGE_URI##*:}"
}

main() {
    validate_input
    prepare_existing_container

    trap cleanup EXIT

    log "Authenticating to ECR."
    aws ecr get-login-password --region "${AWS_REGION}" \
        | docker login --username AWS --password-stdin "${ECR_REGISTRY}" >/dev/null

    log "Pulling image: ${IMAGE_URI##*:}"
    docker pull "${IMAGE_URI}" >/dev/null

    switch_container
    finish_deployment
}

main "$@"
