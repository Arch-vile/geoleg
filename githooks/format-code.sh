#!/bin/bash

# Format java code with Google formatter
# https://github.com/google/google-java-format
# with Maven plugin https://github.com/coveooss/fmt-maven-plugin
mvn com.coveo:fmt-maven-plugin:format

# Format Kotlin code with Pinterest formatter
mvn antrun:run@ktlint-format
