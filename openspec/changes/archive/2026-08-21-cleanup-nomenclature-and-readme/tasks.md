## 1. Nomenclature unification

- [x] 1.1 Update `pom.xml` `<description>` to `Local-network audio player` and verify the string appears in `mvn help:evaluate -Dexpression=project.description` output
- [x] 1.2 Update Dockerfile OCI `description` label to `Local-network audio player` and verify no other label references MVP/browser positioning
- [x] 1.3 Rename `GravifonMvpIntegrationTest` to `GravifonIntegrationTest` and verify `mvn clean test` passes with the renamed class

## 2. User-facing strings

- [x] 2.1 Change PlaylistController 501 messages to `Playlist mutations are not yet implemented` (three occurrences) and verify `PlaylistControllerWebMvcTest` passes after updating its three message expectations
- [x] 2.2 Confirm `index.html` subtitle already reads `Local-network audio player` and requires no change

## 3. README restructure

- [x] 3.1 Rewrite README.md per design.md decision 3: H1 `Gravifon`, tagline `Local-network audio player`, architecture diagram, bounded-contexts line, build/run/container commands, conventions summary, openspec-first principle statement. Verify no occurrence of `MVP`, no AGENTS.md link, no spec-file links, no "Why build: was removed" section remain (`grep -in "mvp\|agents.md\|build:" README.md` returns nothing)
- [x] 3.2 Standardize README command examples on `mvn clean verify` and `mvn clean verify -Pdocker` per design.md decision 4

## 4. Stable spec hygiene (direct Purpose edit)

- [x] 4.1 Edit `openspec/specs/playlist-management/spec.md` Purpose to remove "supported by the MVP" phrasing (Purpose changes cannot ride deltas) and verify `openspec validate --strict` passes

## 5. Verification

- [x] 5.1 Run `mvn clean verify` and confirm build + JaCoCo gate pass
- [x] 5.2 Run `openspec validate cleanup-nomenclature-and-readme --strict` and confirm the change validates
- [x] 5.3 Run `grep -ri "mvp" src/ README.md pom.xml Dockerfile openspec/specs/` and confirm zero remaining occurrences outside `openspec/changes/archive/`
