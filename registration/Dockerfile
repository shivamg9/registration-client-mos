# ------------------------------------------------------------
# Base image UPDATED to Java 17 (JavaFX 17 compatible)
# ------------------------------------------------------------
FROM eclipse-temurin:17-jdk-jammy


# ------------------------------------------------------------
# Build metadata (UNCHANGED)
# ------------------------------------------------------------
ARG SOURCE
ARG COMMIT_HASH
ARG COMMIT_ID
ARG BUILD_TIME

LABEL source=${SOURCE}
LABEL commit_hash=${COMMIT_HASH}
LABEL commit_id=${COMMIT_ID}
LABEL build_time=${BUILD_TIME}

# ------------------------------------------------------------
# Runtime args (UNCHANGED)
# ------------------------------------------------------------
ARG client_version
ARG healthcheck_url
ARG client_upgrade_server
ARG cert_server_URL
ARG keystore_secret

ENV client_version_env=${client_version}
ENV healthcheck_url_env=${healthcheck_url}
ENV client_upgrade_server_env=${client_upgrade_server}
ENV client_repo_env=${client_repo_url}
ENV artifactory_url_env=${artifactory_url}
ENV host_name_env=${host_name}
ENV keystore_secret_env=${keystore_secret}
ENV signer_timestamp_url_env=${signer_timestamp_url}

# ------------------------------------------------------------
# Locale (UNCHANGED)
# ------------------------------------------------------------
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

# ------------------------------------------------------------
# Container user (UNCHANGED)
# ------------------------------------------------------------
ARG container_user=mosip
ARG container_user_group=mosip
ARG container_user_uid=1001
ARG container_user_gid=1001

# ------------------------------------------------------------
# System deps (UPDATED – JavaFX 17 + shaping libs)
# ------------------------------------------------------------
RUN apt-get update && apt-get install -y --no-install-recommends \
    openjfx \
    fontconfig \
    libfreetype6 \
    libharfbuzz0b \
    libfontconfig1 \
    libx11-6 \
    libxext6 \
    libxi6 \
    libxrender1 \
    unzip \
    zip \
    nginx && \
    groupadd -g ${container_user_gid} ${container_user_group} && \
    useradd -u ${container_user_uid} -g ${container_user_group} -s /bin/sh -m ${container_user} && \
    rm -rf /var/lib/apt/lists/*

# ------------------------------------------------------------
# JavaFX runtime flags (UPDATED – t2k REMOVED)
# ------------------------------------------------------------
ENV JAVA_TOOL_OPTIONS="\
-Dfile.encoding=UTF-8 \
-Dsun.jnu.encoding=UTF-8 \
-Dprism.order=sw \
-Djavafx.font.fallback=true"

# ------------------------------------------------------------
# Fonts (UNCHANGED behavior, safer fontdir)
# ------------------------------------------------------------
RUN mkdir -p /usr/share/fonts/truetype/myanmar
COPY registration-client/src/main/resources/fonts/*.ttf /usr/share/fonts/truetype/myanmar/
RUN fc-cache -f -v

# ------------------------------------------------------------
# App files (UNCHANGED)
# ------------------------------------------------------------
WORKDIR /home/${container_user}
ENV work_dir=/home/${container_user}

ADD registration-client/target ./registration-client/target
ADD registration-test/target ./registration-test/target
ADD registration-test/src/main/resources ./registration-test/resources
ADD configure.sh configure.sh

RUN chmod a+x configure.sh \
    && rm -f ./registration-client/target/*javadoc.jar \
    && rm -f ./registration-client/target/*sources.jar \
    && chown -R ${container_user}:${container_user} /home/${container_user} \
    && chown -R ${container_user}:${container_user} /var/www

ENTRYPOINT ["./configure.sh"]
