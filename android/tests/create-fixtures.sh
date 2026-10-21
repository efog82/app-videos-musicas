#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p assets
ffmpeg -v error -y -f lavfi -i color=red:s=320x240:r=24:d=0.8 -f lavfi -i sine=frequency=96:duration=0.8 -vf drawbox=x=0:y=120:w=320:h=120:color=blue:t=fill -c:v libx264 -threads 2 -bf 0 -g 12 -c:a aac -shortest assets/red-blue.mp4
ffmpeg -v error -y -f lavfi -i color=0x00ff00:s=320x240:r=24:d=0.6 -f lavfi -i sine=frequency=180:duration=0.6 -c:v libx264 -threads 2 -bf 0 -g 12 -c:a aac -shortest assets/green.mp4
ffmpeg -v error -y -display_rotation:v:0 90 -i assets/red-blue.mp4 -c copy assets/rotated.mp4
