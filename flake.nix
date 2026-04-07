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
          jdt-language-server
          curl

          # Python tooling for the legacy SQLite migration script
          python3

          # Container tooling for the local SQL Server dev setup
          docker
          docker-compose
          sqlcmd

          # SQL quality-of-life tools
          # - sqlite: quick local DB inspection
          # - litecli: nicer SQLite prompt with completion
          # - usql: nicer cross-database SQL CLI, including SQL Server
          sqlite
          litecli
          usql
        ];

        # These environment variables make Java tooling work more predictably.
        JAVA_HOME = "${pkgs.jdk21}";

        shellHook = ''
          echo "Entered the COMP 3380 Java dev shell."
          echo "Java:   $(java -version 2>&1 | head -n 1)"
          echo "Python: $(python3 --version 2>&1)"
          echo "Docker: $(docker --version 2>/dev/null || echo unavailable)"
          echo "sqlcmd: $(sqlcmd -? >/dev/null 2>&1 && echo available || echo unavailable)"
          echo "usql:   $(usql --version >/dev/null 2>&1 && echo available || echo unavailable)"
          echo "jdtls:  $(command -v jdtls >/dev/null 2>&1 && echo available || echo unavailable)"
          echo "curl:   $(curl --version >/dev/null 2>&1 && echo available || echo unavailable)"
          echo
          echo "Useful tools in this shell: docker, docker-compose, sqlcmd, usql, jdtls, curl, python3, sqlite3, litecli"
        '';
      };
    });
  };
}
