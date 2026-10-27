# Setup

There are two toolchains, a JDK for the seven Maven modules and Python for the
analytics package. Neither side has a runtime dependency. A first build fetches
only Maven's own plugins and JUnit.

Run the gates once to confirm the platform is green. You will not change the
platform's code. You write a Compose file and a few load scripts, which need
Docker (section 5).

## 1. Java and Maven

- A JDK, version 21 or newer. Check with `java --version`.
- Maven 3.8 or newer. Check with `mvn --version`.

From this directory:

```
mvn -B test
```

## 2. Python

Python 3.11 or newer. Check with `python3 --version`.

pytest is the only thing to install. Put it in a virtual environment rather
than in your system Python:

```
python3 -m venv .venv
.venv/bin/pip install pytest
```

Then run it by path, from `analytics/`:

```
cd analytics
../.venv/bin/pytest
```

Or activate the environment once (`source .venv/bin/activate` on macOS and
Linux, `.venv\Scripts\activate` on Windows) and just run `pytest`. `.venv/` is
already in `.gitignore`.

The package itself uses only the standard library, so there is nothing to
install from `analytics/`.

## 3. The gates

The per-module gate runs in seconds, so you can check one module without waiting
on the whole build. Run these from this directory.

| Gate | Command | Tests in this module |
|---|---|---|
| core-domain | `mvn -q -pl core-domain -am test` | 194 |
| storage | `mvn -q -pl storage -am test` | 80 |
| notifications | `mvn -q -pl notifications -am test` | 44 |
| accounts | `mvn -q -pl accounts -am test` | 19 |
| billing | `mvn -q -pl billing -am test` | 19 |
| http-api | `mvn -q -pl http-api -am test` | 91 |
| cli | `mvn -q -pl cli -am test` | 31 |
| analytics | `pytest` (from `analytics/`) | 150 |
| everything | `mvn -B test` | 478 |

These counts describe the platform as it stands. The 478 on the last row is the
Java total. The 150 Python tests run separately.

`-am` also builds and tests the modules the gate depends on, so the command's own
grand total is larger than the number above. `mvn -pl http-api -am test` runs
core-domain, storage, notifications, and accounts as well. The block for the
module you asked about is the one to read. When an `-am` gate fails, though, read
the FIRST failing module's block rather than that one. Maven stops at the first
failure. Anything reported after it follows from that one.

Green means the command prints nothing and exits zero. `-q` hides the successful
output. Drop `-q` when you want the module's
`Tests run:` line.

## 4. Running it

Compile once, then run either entry point straight off the class files:

```
mvn -q -DskipTests package
```

The HTTP API runs on port 8080. It prints a bearer token for each of the two seeded
accounts, one member and one administrator, so you can try both an owner-only
endpoint and an administrator reading someone else's booking:

```
java -cp "http-api/target/classes:core-domain/target/classes:storage/target/classes:notifications/target/classes:accounts/target/classes" \
  edu.cmu.cs214.campusreserve.http.CampusReserveServer 8080
```

The administrative commands. Each run builds a fresh in-memory platform, so
chain commands with a bare `+` when a later one needs to see what an earlier one
did:

```
java -cp "cli/target/classes:core-domain/target/classes:storage/target/classes:billing/target/classes" \
  edu.cmu.cs214.campusreserve.cli.CampusReserveCli help

java -cp "cli/target/classes:core-domain/target/classes:storage/target/classes:billing/target/classes" \
  edu.cmu.cs214.campusreserve.cli.CampusReserveCli seed + export-events --out events.jsonl
```

That second line writes `events.jsonl`, which the analytics package reads.

## 5. Docker and k6

You need Docker Desktop running. `Dockerfile` builds the HTTP server from section 4 into an image that listens on
port 8080. `proxy/nginx.conf` configures an nginx proxy that spreads requests
across every copy of a service named `api`, and adds an `X-Upstream` header to
each response naming the address of the copy that answered.

k6 runs from its image, `grafana/k6:2.3.0`, as a service in your own
`compose.yaml`, on the same network as everything else. Inside the stack the
proxy is `http://proxy`, and each copy of `api` answers on port 8080 under the
name `docker compose ps` gives it. Two things to know when you run it:

- Pass `--no-deps` to `docker compose run`. Without it, Compose starts the k6
  service's dependencies at their default scale, which can quietly take `api`
  back to one copy.
- After `--scale`, wait several seconds before you run a script. The proxy looks the
  copies up again every five seconds.

## 6. Editor

Any editor or IDE. VS Code, IntelliJ IDEA, and Eclipse all import a multi-module
Maven project from the root `pom.xml`. Point it at this directory. Opened on a module
directory, it will not resolve the sibling modules.

## 7. Your coding agent

Configure your coding agent with this directory as its working directory.
Claude Code picks up `CLAUDE.md`, which imports `AGENTS.md`. Most other tools read
`AGENTS.md` directly. Confirm the agent can see `AGENTS.md` before you start.

## When something is wrong

- **A gate fails.** Read the failure before you do anything else. An unexpected
  failure on a tree nobody has edited is almost always a stale `target/` left
  over from an earlier build, and `mvn -B clean test` settles it. A compile error
  means the copy you have is not the one that ships. Take a fresh copy rather
  than patching this one.
- **Maven cannot resolve a sibling module.** You are probably running from inside
  a module directory. Run the gates from this directory.
- **`pip install pytest` fails with `externally-managed-environment`.** Your
  Python is managed by the operating system and refuses to install into itself
  (PEP 668). Use the virtual environment in step 2. Do not reach for
  `--break-system-packages`.
- **`pip: command not found`.** Use `python3 -m pip` instead of `pip`, or use the
  virtual environment's own pip from step 2.
- **`pytest: command not found` after installing it.** The install went somewhere
  that is not on your PATH. Run it as `python3 -m pytest`, or by path from the
  virtual environment.
- **Anything else.** Ask your agent to help, or ask us if you're stuck!
