#!/bin/sh
set -eu

BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
MAVEN_VERSION="3.9.11"
CACHE_DIR="${HOME}/.m2/wrapper/apache-maven-${MAVEN_VERSION}"
MAVEN_HOME="${CACHE_DIR}/apache-maven-${MAVEN_VERSION}"

if [ ! -x "${MAVEN_HOME}/bin/mvn" ]; then
  mkdir -p "${CACHE_DIR}"
  ARCHIVE="${CACHE_DIR}/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
  URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.tar.gz"

  if command -v curl >/dev/null 2>&1; then
    curl -fL "${URL}" -o "${ARCHIVE}"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "${ARCHIVE}" "${URL}"
  else
    echo "curl or wget is required to bootstrap Maven." >&2
    exit 1
  fi

  tar -xzf "${ARCHIVE}" -C "${CACHE_DIR}"
  rm -f "${ARCHIVE}"
fi

exec "${MAVEN_HOME}/bin/mvn" "$@"
