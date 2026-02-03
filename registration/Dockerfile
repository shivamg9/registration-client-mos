# ------------------------------------------------------------
# Java 17 + JavaFX 17 runtime (REQUIRED for Myanmar)
# ------------------------------------------------------------
FROM openjdk:17-jdk-slim

# --- UTF-8 Locale ---
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

# ------------------------------------------------------------
# Install JavaFX 17 + native text shaping libraries
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
    rm -rf /var/lib/apt/lists/*

# ------------------------------------------------------------
# JavaFX runtime flags (safe defaults for containers)
# ------------------------------------------------------------
ENV JAVA_TOOL_OPTIONS="\
-Dfile.encoding=UTF-8 \
-Dsun.jnu.encoding=UTF-8 \
-Dprism.order=sw \
-Djavafx.font.fallback=true"

# ------------------------------------------------------------
# Install your local Myanmar font(s)
# ------------------------------------------------------------
RUN mkdir -p /usr/share/fonts/truetype/myanmar
COPY registration-client/src/main/resources/fonts/*.ttf /usr/share/fonts/truetype/myanmar/
RUN fc-cache -f -v

# ------------------------------------------------------------
# App setup
# ------------------------------------------------------------
WORKDIR /app
COPY registration-client/target ./registration-client/target
COPY registration-test/target ./registration-test/target
COPY registration-test/src/main/resources ./registration-test/resources
COPY configure.sh .

RUN chmod +x configure.sh

ENTRYPOINT ["./configure.sh"]
