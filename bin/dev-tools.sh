#!/usr/bin/env bash

set -eu

FILE_TO_POST="$1"
ACTION_TO_TAKE="${2:-toJson}"
ACTION_PARAM='jenkinsfile'
if [[ "toJenkinsfile" == "${ACTION_TO_TAKE}" ]]; then
  ACTION_PARAM='json'
fi
#JENKINS_DOMAIN="jenkins-jx.ci.datameer-cloud.com"
JENKINS_URL="https://${JENKINS_DOMAIN}"
NETRC_FILE="machine ${JENKINS_DOMAIN}
  login ${JENKINS_USER}
  password ${JENKINS_PASS}
"

crumb=$(curl -s -n --netrc-file <(echo -e "$NETRC_FILE") --cookie-jar /tmp/cookies "$JENKINS_URL/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,\":\",//crumb)")
curl -s -n --netrc-file <(echo -e "$NETRC_FILE") --cookie /tmp/cookies -X POST -H $crumb -F "${ACTION_PARAM}=<${FILE_TO_POST}" "$JENKINS_URL/pipeline-model-converter/${ACTION_TO_TAKE}"
