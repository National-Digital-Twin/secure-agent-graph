from debian:bookworm

COPY . /

run apt-get update && apt-get upgrade -y && apt-get install -y docker docker-compose curl jq 

run curl --location --remote-name https://github.com/Orange-OpenSource/hurl/releases/download/6.1.1/hurl_6.1.1_amd64.deb && \
apt update && apt install ./hurl_6.1.1_amd64.deb

WORKDIR /sag-docker/Test/

CMD [ "docker-compose up -d && ./smoke-test-local-auth.sh" ]