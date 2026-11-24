@echo off
REM Runs the k6 load test inside Docker, on the same network as the three app containers.
REM Needs: "docker compose up -d" already running. Takes about 35 seconds.
REM A random API key per run means earlier runs can't affect this one.
docker run --rm -i --network rate-limiter-demo_default -e API_KEY=load%RANDOM% grafana/k6 run - < loadtest.js
