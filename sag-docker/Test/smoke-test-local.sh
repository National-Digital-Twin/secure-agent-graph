#!/bin/sh
## SPDX-License-Identifier: Apache-2.0
## © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
## and is legally attributed to the Department for Business and Trade (UK) as the governing entity.
##
 ##  Licensed under the Apache License, Version 2.0 (the "License");
 ##  you may not use this file except in compliance with the License.
 ##  You may obtain a copy of the License at
 ##
 ##      http://www.apache.org/licenses/LICENSE-2.0
 ##
 ##  Unless required by applicable law or agreed to in writing, software
 ##  distributed under the License is distributed on an "AS IS" BASIS,
 ##  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ##  See the License for the specific language governing permissions and
 ##  limitations under the License.

# Requires hurl https://hurl.dev/docs/installation.html
# Requires jq (https://jqlang.org/download/)
# requires cognito to be running
# Debugging end process if tests fail: kill -9 $(lsof -ti:3030)

echo "Starting test"

wait_for_url () {
    echo "Testing $1..."
    printf 'GET %s\nHTTP 200' "$1" | hurl --retry "$2" > /dev/null;
    return 0
}

wait_for_url_auth () {
    echo "Testing $1 with auth..."
    printf 'GET %s\nAuthorization: bearer %s\nHTTP 200' "$1" $3 | hurl --retry "$2";# > /dev/null;
    return 0
}

SAG_DIR=../..
USER_1_DATA="http://example/person4321"
USER_2_DATA="http://example/person9876"
SAG_SERVER=http://localhost:3030
USER_1="test+user+admin@ndtp.co.uk"
USER_2="test+user@ndtp.co.uk"


echo Fetch id tokens
ID_TOKEN_1=$(aws --endpoint http://0.0.0.0:9229 cognito-idp initiate-auth --client-id 6967e8jkb0oqcm9brjkrbcrhj --auth-flow USER_PASSWORD_AUTH --auth-parameters USERNAME=$USER_1,PASSWORD=password | jq -r '.AuthenticationResult.IdToken')
ID_TOKEN_2=$(aws --endpoint http://0.0.0.0:9229 cognito-idp initiate-auth --client-id 6967e8jkb0oqcm9brjkrbcrhj --auth-flow USER_PASSWORD_AUTH --auth-parameters USERNAME=$USER_2,PASSWORD=password | jq -r '.AuthenticationResult.IdToken')

echo Starting vanilla secure-agent-graph
USER_ATTRIBUTES_URL=http://localhost:8091 JWKS_URL=disabled \
java \
-Dfile.encoding=UTF-8 \
-Dsun.stdout.encoding=UTF-8 \
-Dsun.stderr.encoding=UTF-8 \
-classpath "$SAG_DIR/sag-server/target/classes:$SAG_DIR/sag-system/target/classes:$SAG_DIR/sag-docker/target/dependency/*" \
uk.gov.dbt.ndtp.secure.agent.graph.SecureAgentGraph \
--config  ../mnt/config/dev-server-vanilla.ttl &

wait_for_url "$SAG_SERVER/ds" 60
hurl hurl/upload-data-no-auth.hurl --variable SAG_SERVER=$SAG_SERVER || exit 1
hurl hurl/sparql-no-auth.hurl --variable SAG_SERVER=$SAG_SERVER --variable USER_1_DATA=$USER_1_DATA --variable USER_2_DATA=$USER_2_DATA || exit 1
hurl hurl/sqarql-text-no-auth.hurl --variable SAG_SERVER=$SAG_SERVER --variable USER_1_DATA=$USER_1_DATA --variable USER_2_DATA=$USER_2_DATA || exit 1

# --------------------------------

PID=$(kill %+)
echo $PID
wait $PID

# --------------------------------

echo Starting secure-agent-graph with authentication
USER_ATTRIBUTES_URL=http://localhost:8091 JWKS_URL=http://localhost:9229/local_6GLuhxhD/.well-known/jwks.json \
java \
-Dfile.encoding=UTF-8 \
-Dsun.stdout.encoding=UTF-8 \
-Dsun.stderr.encoding=UTF-8 \
-classpath "$SAG_DIR/sag-server/target/classes:$SAG_DIR/sag-system/target/classes:$SAG_DIR/sag-docker/target/dependency/*" \
uk.gov.dbt.ndtp.secure.agent.graph.SecureAgentGraph \
--config ../mnt/config/dev-server-graphql.ttl &


echo Wait for server to be ready

wait_for_url_auth "$SAG_SERVER/ds" 60 $ID_TOKEN_1

hurl hurl/upload-data-auth.hurl  --variable SAG_SERVER=$SAG_SERVER --variable ID_TOKEN=$ID_TOKEN_1 || exit 1


hurl hurl/sparql-auth-admin-user.hurl --variable SAG_SERVER=$SAG_SERVER \
--variable ID_TOKEN_USER_1=$ID_TOKEN_1 \
--variable ID_TOKEN_USER_2=$ID_TOKEN_2 \
--variable USER_1_DATA=$USER_1_DATA \
--variable USER_2_DATA=$USER_2_DATA || exit 1


PID=$(kill %+)
wait $PID

echo "Passed"
exit 0
