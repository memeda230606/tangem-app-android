# token-gen

Generates Kotlin (Jetpack Compose) source files from design tokens defined in the optional `ds-tokens` git submodule.

The Android project uses the generated files committed under
`core/ui/src/main/java/com/tangem/core/ui/res/generated/` by default, so a standalone checkout does not need the
`ds-tokens` submodule to build.

To strictly verify the committed files against a local `ds-tokens` checkout, run:

```bash
./gradlew :core:ui:verifyDesignTokens -PstrictDesignTokens=true
```

## Updating tokens

1. Initialize or update the optional `ds-tokens` submodule:
   ```bash
   git submodule update --remote core/ui/ds-tokens
   ```
2. Re-run the build:
   ```bash
   cd core/ui/token-gen && npm run build
   ```
3. Commit both the submodule pointer and generated files.

## How it works

The script uses [Style Dictionary v5](https://styledictionary.com/) with [@tokens-studio/sd-transforms](https://github.com/tokens-studio/sd-transforms) to read JSON token files from `core/ui/ds-tokens/tokens/` and generate Kotlin files into `core/ui/src/main/java/com/tangem/core/ui/res/generated/`.

All generated files are written to `com.tangem.core.ui.res.generated` and should not be edited manually.
