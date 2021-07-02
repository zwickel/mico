#!/bin/bash

# This script installs MICO with all its dependencies.
echo -e "MICO Setup\n----------"

# Check if DockerHub credentials are already provided
if [[ -z "${DOCKERHUB_USERNAME}" || -z "${DOCKERHUB_PASSWORD}" || -z "${DOCKERHUB_URL}" ]]; then
    echo "ERROR: One or more environment variables for DockerHub are not specified. Please, specify DOCKERHUB_USERNAME, DOCKERHUB_PASSWORD for accessing DockerHub."
    echo "Additionally, specify the Docker registry URL in DOCKERHUB_URL variable. If not specified, the default value is 'docker.io/ustmico'."
    exit 1
fi

# Change directory so Kubernetes configurations can be applied with relative path
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd $DIR

echo -e "\nCreate Kubernetes resources\n---------------------------"

# Create MICO namespaces
kubectl apply -f mico-namespaces.yaml

# Create ClusterRoleBinding for mico-system
kubectl apply -f mico-cluster-admin.yaml

# Prepare MICO build bot namespace
envsubst < mico-build-bot.yaml | kubectl apply -f -

# Install MICO components
kubectl apply -f neo4j.yaml
kubectl apply -f redis.yaml

if [[ -z "DOCKERHUB_URL" ]]; then
    kubectl apply -f mico-core.yaml
else
    cp mico-core.yaml mico-core-non-default-registry.yaml
    sed -i -- "s#docker.io/ustmico#"${DOCKERHUB_URL}"#g" mico-core-non-default-registry.yaml
    kubectl apply -f mico-core-non-default-registry.yaml
    rm mico-core-non-default-registry.yaml
fi

# Set public IP address for MICO dashboard
export MICO_PUBLIC_IP
envsubst < mico-admin.yaml | kubectl apply -f -

# Install external components
kubectl apply -f ./kube-state-metrics
kubectl apply -f https://storage.googleapis.com/tekton-releases/pipeline/latest/release.yaml

# Setup Kafka
kubectl apply -k ./kafka/variants/dev-small/

# Setup OpenFaaS
if [[ -z "${OPENFAAS_PORTAL_PASSWORD}" ]]; then
    OPENFAAS_PORTAL_PASSWORD=$(head -c 12 /dev/urandom | shasum| cut -d' ' -f1)
fi

kubectl -n openfaas create secret generic basic-auth \
--from-literal=basic-auth-user=admin \
--from-literal=basic-auth-password="$OPENFAAS_PORTAL_PASSWORD"

kubectl -n monitoring create secret generic basic-auth \
--from-literal=basic-auth-user=admin \
--from-literal=basic-auth-password="$OPENFAAS_PORTAL_PASSWORD"

kubectl apply -f ./monitoring
kubectl apply -f ./openfaas

# Set public IP address for OpenFaaS Portal (only if given by user)
if [[ -z "OPENFAAS_PORTAL_PUBLIC_IP" ]]; then
    sed '/${OPENFAAS_PORTAL_PUBLIC_IP}/d' openfaas/gateway-external-svc.yaml | kubectl apply -f -
else
    export OPENFAAS_PORTAL_PUBLIC_IP
    envsubst < openfaas/gateway-external-svc.yaml | kubectl apply -f -
fi

echo -e "\nOpenFaaS Portal Admin password:"
echo $OPENFAAS_PORTAL_PASSWORD
echo -e "\nScript execution finished!"
