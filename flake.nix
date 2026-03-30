{
  # A flake is a reproducible Nix project description.
  #
  # In this repo, the flake is only used to define a development shell.
  # That means it does NOT change how the project works for teammates who do
  # not use Nix. It is just an optional way to get the same tools installed.
  #
  # Typical usage:
  #   1. Install Nix
  #   2. Run: nix develop
  #   3. Work as usual inside the shell
  #
  # The exact package versions are pinned by flake.lock, which is committed to
  # git alongside this file.

  description = "Optional reproducible Java dev shell for the COMP 3380 final project";

  inputs = {
    # We follow a stable nixpkgs branch, then flake.lock pins it to one exact commit.
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-25.05";
  };

  outputs = {
    self,
    nixpkgs,
  }: let
    # These are the common laptop / lab machine platforms we may care about.
    supportedSystems = [
      "x86_64-linux"
      "aarch64-linux"
      "x86_64-darwin"
      "aarch64-darwin"
    ];

    forEachSystem = f:
      nixpkgs.lib.genAttrs supportedSystems (system: f system);
  in {
    devShells = forEachSystem (system: let
      pkgs = import nixpkgs {
        inherit system;
        config.allowUnfree = false;
      };
    in {
      default = pkgs.mkShell {
        packages = with pkgs; [
          # Java toolchain
          jdk21
          maven

          # SQL quality-of-life tools
          # - sqlite: quick local DB inspection
          # - litecli: nicer SQLite prompt with completion
          sqlite
          litecli
        ];

        # These environment variables make Java tooling work more predictably.
        JAVA_HOME = "${pkgs.jdk21}";

        shellHook = ''
          export MAVEN_OPTS="-Dmaven.repo.local=$PWD/.m2/repository"

          echo "Entered the COMP 3380 Java dev shell."
          echo "Java:   $(java -version 2>&1 | head -n 1)"
          echo "Maven:  $(mvn -v 2>/dev/null | head -n 1)"
          echo
          echo "Useful SQL tools in this shell: sqlite3, litecli"
        '';
      };
    });
  };
}
