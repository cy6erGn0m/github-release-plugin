# github-release-plugin
Maven plugin for uploading artifacts to the GitHub releases

## Setup

- Add git URL (SSH or https url)
```xml
    <scm>
        <connection>scm:git:git@github.com:owner/repository.git</connection>
        <tag>HEAD</tag>
    </scm>
```
- Add plugin 
```xml
    <pluginRepositories>
        <pluginRepository>
            <snapshots>
                <enabled>false</enabled>
            </snapshots>
            <id>bintray-cy6ergn0m-maven</id>
            <name>bintray-plugins</name>
            <url>http://dl.bintray.com/cy6ergn0m/maven</url>
        </pluginRepository>
    </pluginRepositories>

    <build>
        <plugins>
            <plugin>
                <groupId>cy.github</groupId>
                <artifactId>github-release-plugin</artifactId>
                <version>${plugin.version}</version>

                <configuration>
                    <tagName>${project.artifactId}-${project.version}</tagName>
                    <preRelease>true</preRelease>
                </configuration>

                <executions>
                    <execution>
                        <goals>
                            <goal>gh-upload</goal>
                        </goals>
                        <phase>deploy</phase>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
```

- Obtain token at github: https://github.com/settings/tokens
- Open your [~/.m2/settings.xml](https://maven.apache.org/settings.html) and add server with the token. If you use TeamCity you have to configure [settings.xml for you build configuration](https://confluence.jetbrains.com/display/TCD9/Maven+Server-Side+Settings)
 
```xml
     <server>
        <id>github</id>
        <username>username</username>
        <password>(your token here)</password>
    </server>
```

## Deploy releases

To deploy to github use `mvn deploy` or use [Maven Release Plugin](http://maven.apache.org/maven-release/maven-release-plugin/)

You also can launch plugin directly `mvn package github-release-plugin:gh-upload`

## Plugin configuration options
| Option | System property | Default value | Description |
| ------ | ----------------| ------------- | ----------- |
| `skip` | `maven.deploy.skip` | `false` | Skip deployment, notice that *system property* will skip all deployments and will affect all plugins |
| `serverId` |  | `github` | server id in settings.xml |
| `endpointURL` | | `https://api.github.com` | URL of github API, also could be specified via settings.xml server entry |
| `owner` | | | github repository owner, overrides `scm` tag |
| `repository` | | | github repository id |
| `tagName` | | `${project.version}` | git tag to be used to create release from | 
| `releaseTitle` | | `${project.version}` | title of release |
| `preRelease` | | `false` | If `true` then release will be marked with red prerelese badge |

