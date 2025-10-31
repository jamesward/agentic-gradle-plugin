## Release Process

Update versions in `README.md`

```
ORG_GRADLE_PROJECT_mavenCentralUsername=username
ORG_GRADLE_PROJECT_mavenCentralPassword=the_password

ORG_GRADLE_PROJECT_signingInMemoryKey=exported_ascii_armored_key
ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=some_password
```

```
./gradlew publishAndReleaseToMavenCentral
```

## TODO
- MCP Client?
- CI/CD for publishing
- Console for arbitrary runs
