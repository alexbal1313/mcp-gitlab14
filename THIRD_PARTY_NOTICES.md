# Third-party notices

The project's original code is licensed under [MIT](LICENSE).
Third-party components retain their own licenses.

## Apache Maven Wrapper 3.3.4

Files: `mvnw`, `mvnw.cmd`.

License: [Apache License 2.0](LICENSES/Apache-2.0.txt). The upstream license
headers are preserved. The scripts match the official 3.3.4 only-script
distribution without modifications.

The following notice is reproduced from the
[Apache Maven Wrapper 3.3.4 source release](https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.4/maven-wrapper-3.3.4-source-release.zip):

> Apache Maven Wrapper  
> Copyright 2013-2022 The Apache Software Foundation
>
> This product includes software developed at  
> The Apache Software Foundation (http://www.apache.org/).
>
> The original idea and initial implementation of the maven-wrapper module is derived  
> from the Gradle Wrapper which was written originally by Hans Dockter and Adam Murdoch.  
> Copyright 2007 the original author or authors.

## Runtime dependencies and container base image

Maven dependencies are not relicensed under the project's MIT license.
Their upstream licenses and notices remain applicable, including notices
inside dependency JARs. The Red Hat UBI / OpenJDK runtime image has its own
licenses and terms.

Before redistributing a compiled application or container image, retain the
licenses and notices of the exact dependency and base-image versions included
in that artifact. This file is not an exhaustive transitive dependency
license audit.
