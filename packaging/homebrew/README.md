# Homebrew tap

On an Apple Silicon Mac, Container Dashboard installs with Homebrew, and `brew upgrade` keeps it up to date:

```bash
brew install --cask beytullah-gunduz/tap/container-dashboard
```

The cask lives in [beytullah-gunduz/homebrew-tap](https://github.com/beytullah-gunduz/homebrew-tap), next to the casks of other projects. This folder holds its template (`container-dashboard.rb`) and the script that fills in a release's version and checksum (`render-cask.sh`). After every stable tag, the `homebrew` job in `.github/workflows/release.yml` renders the cask and pushes it to the tap. Pre-release tags (with a hyphen) are skipped, and re-tagging an older version never rolls the tap back.

To change the cask, for example what `brew uninstall --zap` removes, edit the template here. The next release publishes it.

## One-time setup

The tap repository already exists. This repository needs a deploy key that can write to the tap and to nothing else, with its private half stored as the `HOMEBREW_TAP_DEPLOY_KEY` secret:

```bash
ssh-keygen -t ed25519 -N "" -C "container-dashboard release" -f homebrew_tap_key
gh repo deploy-key add homebrew_tap_key.pub --repo beytullah-gunduz/homebrew-tap --allow-write --title "container-dashboard release"
gh secret set HOMEBREW_TAP_DEPLOY_KEY --repo beytullah-gunduz/container-dashboard < homebrew_tap_key
rm homebrew_tap_key homebrew_tap_key.pub
```

The next tag publishes the cask. To publish the current release right away, run this from the repository root, with that release's version:

```bash
version=1.4.0
gh repo clone beytullah-gunduz/homebrew-tap /tmp/homebrew-tap
mkdir -p /tmp/homebrew-tap/Casks
sha256=$(gh release download "v$version" --repo beytullah-gunduz/container-dashboard --pattern "ContainerDashboard-$version.dmg.sha256" --output - | cut -d' ' -f1)
packaging/homebrew/render-cask.sh "$version" "$sha256" > /tmp/homebrew-tap/Casks/container-dashboard.rb
git -C /tmp/homebrew-tap add Casks
git -C /tmp/homebrew-tap commit -m "chore(container-dashboard): update to $version"
git -C /tmp/homebrew-tap push
```

Without the secret, the `homebrew` job fails with an error that points here. The GitHub Release itself is already published by then.

## What users run into

- **Gatekeeper.** The app is not signed with a Developer ID and not notarized. Homebrew marks every cask download as coming from the internet, and it no longer offers `--no-quarantine`, so macOS blocks the first launch after every install and every upgrade until the user clicks **Open Anyway** in System Settings → Privacy & Security. The official `homebrew/cask` repository does not accept unsigned apps, which is why the cask lives in a tap of its own. Signing and notarizing the app with an Apple Developer ID would remove the prompt.
- **Tap trust.** Homebrew loads casks from a third-party tap only once they are trusted. Installing with the full name, as above, trusts this one cask. A bare `brew install --cask container-dashboard` after `brew tap` is refused until `brew trust --cask beytullah-gunduz/tap/container-dashboard`.
- **An earlier DMG install.** Homebrew stops when `/Applications/ContainerDashboard.app` already exists. Delete it first. Settings live in `~/Library/Application Support/container-dashboard` and are kept.
- **Intel Macs** can't install the cask, because the DMG is built for Apple Silicon only.
