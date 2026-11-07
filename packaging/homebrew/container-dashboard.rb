# Template for Casks/container-dashboard.rb in beytullah-gunduz/homebrew-tap.
# render-cask.sh fills in the version and checksum; the release workflow
# publishes the result to the tap after every stable tag.
cask "container-dashboard" do
  version "@VERSION@"
  sha256 "@SHA256@"

  url "https://github.com/beytullah-gunduz/container-dashboard/releases/download/v#{version}/ContainerDashboard-#{version}.dmg"
  name "Container Dashboard"
  desc "Desktop dashboard for Docker and other container engines"
  homepage "https://github.com/beytullah-gunduz/container-dashboard"

  livecheck do
    url :url
    strategy :github_latest
  end

  # The release DMG is built on an Apple Silicon runner only.
  depends_on arch: :arm64
  depends_on :macos

  app "ContainerDashboard.app"

  zap trash: "~/Library/Application Support/container-dashboard"
end
