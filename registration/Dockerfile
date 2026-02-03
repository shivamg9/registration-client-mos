FROM mosipdev/javafx-reg-client-openjdk:latest

ARG SOURCE
ARG COMMIT_HASH
ARG COMMIT_ID
ARG BUILD_TIME

LABEL source=${SOURCE}
LABEL commit_hash=${COMMIT_HASH}
LABEL commit_id=${COMMIT_ID}
LABEL build_time=${BUILD_TIME}

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

# --- Burmese Language & Encoding Support ---
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

# --- Container user ---
ARG container_user=mosip
ARG container_user_group=mosip
ARG container_user_uid=1001
ARG container_user_gid=1001

# --- Install system dependencies for JavaFX & fonts ---
RUN sed -i 's|http://deb.debian.org|http://archive.debian.org|g' /etc/apt/sources.list && \
    sed -i 's|http://security.debian.org|http://archive.debian.org|g' /etc/apt/sources.list && \
    echo 'Acquire::Check-Valid-Until "false";' > /etc/apt/apt.conf.d/99no-check-valid-until && \
    apt-get -y update && \
    apt-get install -y --no-install-recommends \
        unzip \
        zip \
        nginx \
        fontconfig \
        libpango-1.0-0 \
        libpangoft2-1.0-0 \
        libgtk-3-0 \
        libx11-6 \
        libxext6 \
        libxi6 \
        libxrender1 && \
    groupadd -g ${container_user_gid} ${container_user_group} && \
    useradd -u ${container_user_uid} -g ${container_user_group} -s /bin/sh -m ${container_user} && \
    rm -rf /var/lib/apt/lists/*

# --- JavaFX system properties (software rendering & font dir) ---
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 \
-Dsun.jnu.encoding=UTF-8 \
-Dprism.order=sw \
-Dprism.font.fallback=true"

# --- Install Padauk font ---
RUN mkdir -p /usr/share/fonts/truetype/myanmar
COPY registration-client/src/main/resources/fonts/Padauk.ttf /usr/share/fonts/truetype/myanmar/Padauk.ttf
COPY 99-myanmar.conf /etc/fonts/conf.d/99-myanmar.conf
RUN fc-cache -f -v

# --- Working directory ---
WORKDIR /home/${container_user}
ENV work_dir=/home/${container_user}

# --- Application artifacts ---
ADD registration-client/target ./registration-client/target
ADD registration-test/target ./registration-test/target
ADD registration-test/src/main/resources ./registration-test/resources
ADD configure.sh configure.sh

# --- Fix permissions ---
RUN chmod a+x configure.sh \
    && rm -f ./registration-client/target/registration-client-*-javadoc.jar \
    && rm -f ./registration-client/target/registration-client-*-sources.jar \
    && chown -R ${container_user}:${container_user} /home/${container_user} \
    && chown -R ${container_user}:${container_user} /var/www

# --- Switch to container user ---
#USER ${container_user_uid}:${container_user_gid}

ENTRYPOINT ["./configure.sh"]
