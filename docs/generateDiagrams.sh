#!/usr/bin/env bash
## SPDX-License-Identifier: Apache-2.0
 ##
 ##  Copyright (c) Telicent Ltd.
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
 ##
 ##
 ##  This file is unmodified from its original version developed by Telicent Ltd.,
 ##  and is now included as part of a repository maintained by the National Digital Twin Programme.
 ##  All support, maintenance and further development of this code is now the responsibility
 ##  of the National Digital Twin Programme.

SCRIPT_DIR=$(dirname "${BASH_SOURCE[0]}")
SCRIPT_DIR=$(cd "${SCRIPT_DIR}" && pwd)

if [ ! command -v mmdc ] >/dev/null 2>&1; then
  echo "Required mmdc command not found, please install by following instructions from https://github.com/mermaid-js/mermaid-cli"
  exit 1
fi

IMAGE_DIR=${SCRIPT_DIR}/images
echo "Processing Mermaid diagrams in directory ${SCRIPT_DIR}/diagrams/"
echo ""
for DIAGRAM in $(ls ${SCRIPT_DIR}/diagrams/*.mmd); do
  FILENAME=$(basename ${DIAGRAM})
  FILENAME=${FILENAME%%\.*}
  SVG_NAME="${IMAGE_DIR}/${FILENAME}.svg"
  PNG_NAME="${IMAGE_DIR}/${FILENAME}.png"
  echo "Generating SVG Image for Diagram $(basename ${DIAGRAM})..."
  mmdc -i ${DIAGRAM} -o ${SVG_NAME}
  echo "Generating PNG Image for Diagram $(basename ${DIAGRAM})..."
  mmdc -i ${DIAGRAM} -o ${PNG_NAME}
  echo ""
done
