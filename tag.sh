#!/bin/bash
set -e
AWS_ACCOUNT=$1
IMAGE_NAME=$2
if [ -z "${AWS_ACCOUNT}" ] || [ -z "${IMAGE_NAME}" ]
then
    echo "Useage: ./tag.sh <aws-account-number> <image_name>"
    exit 1
fi
REPO="${AWS_ACCOUNT}.dkr.ecr.eu-west-1.amazonaws.com"
docker tag "${IMAGE_NAME}" "${REPO}/${IMAGE_NAME}:latest"
docker push --all-tags "${REPO}/${IMAGE_NAME}"
