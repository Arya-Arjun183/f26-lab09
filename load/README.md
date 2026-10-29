# Load checks

```sh
docker compose up -d --build --scale api=2
sleep 6
docker compose logs api
docker compose ps
```

Run the proxy/token check with one `u-1` token from either API log:

```sh
docker compose run --rm --no-deps -e TOKEN='u-1 token' k6 run /scripts/tokens-any-copy.js
```

It must print two distinct `upstream=` values before its failing threshold is interpreted.

Run the booking check with the two container names and their matching `u-1` tokens:

```sh
docker compose run --rm --no-deps \
  -e API_ONE='http://f26-lab09-api-1:8080' -e TOKEN_ONE='api-1 token' \
  -e API_TWO='http://f26-lab09-api-2:8080' -e TOKEN_TWO='api-2 token' \
  k6 run /scripts/booking-at-most-once.js
```

Substitute names from `docker compose ps` if the Compose project name differs.
