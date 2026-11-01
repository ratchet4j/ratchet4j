# Releasing

ratchet4j ships **every second month**, automatically, plus a patch release whenever a bug needs
one. This page explains how it works and what to do for the first release.

## The rules

- **Cycle:** a release is started on the 1st of January, March, May, July, September and November
  (06:00 UTC) by the *Scheduled release* workflow.
- **It only releases when it should:** there must be an earlier release, new commits since it, and
  a green CI on the latest commit of `main`. Otherwise it stops with a clear message.
- **Versions** follow [Semantic Versioning](https://semver.org/). Scheduled releases bump the
  **minor** number (0.1.0, 0.2.0, ...). Bug-fix releases bump the **patch** number (0.1.1). While the
  major version is 0, the CLI and the baseline schema may change between releases.
- **Nothing is committed to release.** The poms stay at `0.1.0-SNAPSHOT`; the release workflow
  stamps the real version into the build (`versions:set`) and the jar reports it with
  `ratchet --version`. The tag is created at the exact commit that was built and tested.
- **Every release is built from scratch and tested** (full test suite, a smoke test of the packaged
  jar, and a check that the jar reports the right version) before anything is published.

## What a release produces

| Output | Where |
|---|---|
| `ratchet-<version>.jar`, a runnable jar for Java 17+ | GitHub Release |
| `ratchet-<version>.jar.sha256`, its checksum | GitHub Release |
| Release notes from `CHANGELOG.md` | GitHub Release |
| Signed artifacts for Maven Central | Maven Central (only after the one-time setup below) |

## First release (v0.1.0): step by step

1. **Get CI green.** Push to `main` and wait until the **CI OK** check passes (build on Java 17 and
   21, Windows, macOS, self-check, links and workflow lint). If a job fails, fix it first.
2. **Create the `release` environment** (Settings > Environments > New environment > `release`).
   Optional but recommended: add yourself as a *required reviewer*. Then every release, including
   the automatic ones, waits for your click.
3. **Rehearse:** Actions > **Release** > Run workflow, branch `main`, version `0.1.0`,
   leave **Dry run** ticked. Open the run summary: it shows the checksum and the release notes, and
   the `release-files` artifact contains the jar.
4. **Release:** run it again with **Dry run unticked**. For the very first (alpha) release also
   tick **Pre-release**. The workflow creates the tag `v0.1.0` and the GitHub Release with the jar,
   the checksum and the notes.
5. **Check the result:** download the jar, run `sha256sum -c ratchet-0.1.0.jar.sha256` and
   `java -jar ratchet-0.1.0.jar --version`; it must print `ratchet4j 0.1.0`.
6. **Tidy up:** in `CHANGELOG.md` the `[Unreleased]` section stays empty and the released section
   has the version and date.

Alternative to steps 3 and 4: push a tag (`git tag v0.1.0 && git push origin v0.1.0`). The same
workflow runs and publishes.

## Scheduled releases

After the first release nothing needs doing: on the 1st of every second month the workflow decides,
and if everything is green it starts the *Release* workflow with the next minor version.

- **See what it would do:** Actions > *Scheduled release* > Run workflow. Choose `patch` for a
  hot-fix release or tick *force* to release without new commits.
- **Add a safety net:** reviewers on the `release` environment (see above).
- **Pause it:** disable the *Scheduled release* workflow in the Actions tab.
- **Note:** GitHub switches off scheduled workflows in a public repository after 60 days without
  any repository activity; a push re-enables them. If a cycle was missed, run the workflow by hand.

### Before each release (5 minutes)

- Add the user-visible changes to `## [Unreleased]` in `CHANGELOG.md` as you merge them. The release
  notes use the section named after the version, or `[Unreleased]` if there is none.
- After the release, rename `[Unreleased]` to the version and date, and add a fresh empty
  `[Unreleased]` above it.

## One-time setup for Maven Central (not needed for GitHub releases)

> **Status: prepared but untested.** The `release` profile in `pom.xml` and the publish step in
> `release.yml` were written from the Central Portal documentation but have never run against
> Maven Central. Expect to fix small things on the first attempt. Publishing to Central is
> permanent: a published version can never be changed or removed.

1. Sign in at <https://central.sonatype.com> with GitHub and **register the namespace
   `io.github.ratchet4j`**. The portal asks you to verify it by creating a temporary public
   repository with a name it gives you, inside the `ratchet4j` org (you must be an owner).
2. **Create a GPG key** and publish the public part:
   `gpg --full-generate-key`, then `gpg --keyserver keyserver.ubuntu.com --send-keys <KEYID>`.
   Export the private key for CI with `gpg --armor --export-secret-keys <KEYID>`. Keep an offline backup.
3. In the portal, **generate a user token** (a username and a password pair).
4. In the repository: Settings > Secrets and variables > Actions:

   | Kind | Name | Value |
   |---|---|---|
   | secret | `CENTRAL_USERNAME` | token username |
   | secret | `CENTRAL_PASSWORD` | token password |
   | secret | `GPG_PRIVATE_KEY` | the armored private key |
   | secret | `GPG_PASSPHRASE` | the key's passphrase |
   | variable | `PUBLISH_TO_CENTRAL` | `true` to turn publishing on |
   | variable | `CENTRAL_AUTO_PUBLISH` | leave unset (`false`) at first |

5. With `CENTRAL_AUTO_PUBLISH` off, the release uploads a **deployment** that waits in the portal.
   Inspect it there, click **Publish** yourself, and only after a few clean releases switch
   `CENTRAL_AUTO_PUBLISH` to `true`.

## If something goes wrong

| Problem | What to do |
|---|---|
| The release workflow fails before publishing | Fix `main` and run it again; nothing was published. |
| It failed after the GitHub Release was created | Delete the release and the tag (`git push --delete origin vX.Y.Z`) and run again, or publish a patch version. |
| A bad version reached Maven Central | It cannot be removed. Publish a fixed patch version and say so in the changelog. |
| The scheduled run says CI is not green | Fix `main` first; run the scheduled workflow manually afterwards. |
| The self-check fails after you changed code | You introduced a Google-style violation. Fix it, or if it is intended, see CONTRIBUTING.md ("The self-check"). |

## Branch protection (recommended)

Settings > Branches > add a rule for `main`: require a pull request and require the status check
**CI OK**. That single check is green only when every CI job passed.
