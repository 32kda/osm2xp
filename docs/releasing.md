# Releasing OSM2XP

Releases are produced by GitHub Actions (`.github/workflows/maven.yml`).

## Cut a release

1. Bump the version everywhere it appears (Maven `pom.xml` files, OSGi
   `Bundle-Version` manifests, `CoreConstants.OSM2XP_VERSION`, the about dialog
   in `plugin.xml`) and add a `CHANGELOG.md` section.
2. Commit, then create an annotated tag and push both the branch and the tag:

   ```
   git tag -a 4.7.0 -m "OSM2XP 4.7.0"
   git push origin master 4.7.0
   ```

3. The tag build publishes a GitHub Release with 4 assets
   (`osm2xp`/`osm2xpc`, Windows + Linux). GitHub also exposes the 2 source
   archives, giving 6 files in total.

## Mirroring to SourceForge

SourceForge cannot pull GitHub release assets by itself, so the workflow pushes
the 4 assets to SourceForge's File Release System (FRS) on every tag. They are
then distributed by SF's download mirror network at
`https://sourceforge.net/projects/osm2xp/files/<version>/`.

One-off setup:

1. Generate a dedicated key pair (no passphrase so CI can use it):

   ```
   ssh-keygen -t ed25519 -C "osm2xp-ci" -f sf_deploy -N ""
   ```

2. Add the **public** key (`sf_deploy.pub`) in SourceForge under
   *Account → SSH settings*. The account must be a project admin (or hold the
   "release technician" permission) for the `osm2xp` project.

3. In the GitHub repo, go to *Settings → Secrets and variables → Actions* and
   add:
   - `SOURCEFORGE_USER` — your SourceForge username
   - `SOURCEFORGE_SSH_KEY` — the contents of the private key file

The workflow rsyncs the files to `/home/frs/project/osm2xp/<tag>/` (creating the
directory if needed). If the secrets are not set — e.g. on a fork — the step
prints a message and is skipped, so builds never fail because of missing
credentials.

## Testing the mirror without pushing a tag

The mirror step can be exercised against an already-published release:

1. Make sure the updated workflow is on the default branch (`master`), since
   manually run workflows are taken from there.
2. Go to *Actions → OSM2XP Tycho Build → Run workflow*, pick branch `master`,
   and set **release_tag** to an existing tag, e.g. `4.7.0`.
3. The run downloads that release's assets from GitHub (`gh release download`)
   and rsyncs them to `/home/frs/project/osm2xp/4.7.0/`. No GitHub release is
   created and no tag is pushed.
4. Verify the files at
   `https://sourceforge.net/projects/osm2xp/files/4.7.0/`.

This validates the credentials and the SourceForge upload path (it reuses the
released assets, so it does not re-test the build itself). Leaving **release_tag**
empty on a manual run performs a normal build with no publishing.

To publish release notes on SourceForge, drop a `README.md` (or another file
with "readme" in the name) into that release folder; the FRS file browser shows
it under the download list.
