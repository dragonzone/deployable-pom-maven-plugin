# deployable-pom-maven-plugin [![Build Status](https://jenkins.dragon.zone/buildStatus/icon?job=dragonzone/deployable-pom-maven-plugin/master)](https://jenkins.dragon.zone/blue/organizations/jenkins/dragonzone%2Fdeployable-pom-maven-plugin/activity?branch=master) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/zone.dragon.maven.plugin/deployable-pom-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/zone.dragon.maven.plugin/deployable-pom-maven-plugin/)

This maven plugin handles resolving the CI-friendly maven properties (`${sha1}`, `${revision}`, and `${changelist}`) in the deployed pom file:

```xml
<plugin>
    <groupId>zone.dragon.maven.plugin</groupId>
    <artifactId>deployable-pom-maven-plugin</artifactId>
    <version>version-goes-here</version>
    <executions>
        <execution>
            <id>create-deployable-pom</id>
            <phase>process-resources</phase>
            <goals>
                <goal>resolve-ci-properties</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```
