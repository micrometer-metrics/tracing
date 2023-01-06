# Micrometer Application Metrics

[![Build Status](https://circleci.com/gh/micrometer-metrics/tracing.svg?style=shield)](https://circleci.com/gh/micrometer-metrics/tracing)
[![Apache 2.0](https://img.shields.io/github/license/micrometer-metrics/tracing.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/io.micrometer/micrometer-tracing.svg)](https://search.maven.org/artifact/io.micrometer/micrometer-tracing)
[![Javadocs](https://www.javadoc.io/badge/io.micrometer/micrometer-tracing.svg)](https://www.javadoc.io/doc/io.micrometer/micrometer-tracing)
[![Revved up by Gradle Enterprise](https://img.shields.io/badge/Revved%20up%20by-Gradle%20Enterprise-06A0CE?logo=Gradle&labelColor=02303A)](https://ge.micrometer.io/)

A application tracing facade.

## Join the discussion

Join the [Micrometer Slack](https://slack.micrometer.io) to share your questions, concerns, and feature requests.

## Snapshot builds

Snapshots are published to `repo.spring.io` for every successful build on the `main` branch and maintenance branches.

To use:

```groovy
repositories {
    maven { url 'https://repo.spring.io/snapshot' }
}

dependencies {
    implementation 'io.micrometer:micrometer-tracing:latest.integration'
}
```

## Milestone releases

Milestone releases are published to https://repo.spring.io/milestone. Include that as a maven repository in your build
configuration to use milestone releases. Note that milestone releases are for testing purposes and are not intended for
production use.

## Documentation

The documentation is available at https://micrometer.io/docs/tracing.

## Contributing

See our [Contributing Guide](CONTRIBUTING.md) for information about contributing to Micrometer Tracing.

## Code formatting

The [spring-javaformat plugin](https://github.com/spring-io/spring-javaformat) is configured to check and apply consistent formatting in the codebase through the build.
The `checkFormat` task checks the formatting as part of the `check` task.
Apply formatting with the `format` task.
You should rely on the formatting the `format` task applies instead of your IDE's configured formatting.

-------------------------------------
_Licensed under [Apache Software License 2.0](https://www.apache.org/licenses/LICENSE-2.0)_

_Sponsored by [VMware](https://tanzu.vmware.com)_
