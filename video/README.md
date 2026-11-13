# ORU product tour (Remotion)

The tour is a Remotion composition over frames recorded from the real app.
Nothing in the film is a mockup: `scripts/promo/tour_scenes.cjs` drives a
headless browser through the live site (typing into the sign-up form, uploading
a release, scrolling the feed, liking a song) and screenshots it continuously at
roughly 10–20 fps. Remotion replays those frames and adds the phone frame,
titles, captions, progress bar, transitions and the score.

## Output

`public/promo/oru-product-tour.mp4` — 48.1 s, 1080×1920, 30 fps, H.264 + AAC.

## Re-record the screens

```sh
node scripts/promo/tour_scenes.cjs
```

It signs in with the demo accounts in `.promo/tour/state.json`, so that file
must exist locally (it is git-ignored). Frames land in `video/public/frames/`
and are also ignored. The sign-up scene creates a fresh account on production
each run.

## Re-render

```sh
cd video
bun install
bunx remotion render src/index.ts Tour ../public/promo/oru-product-tour.mp4 \
  --browser-executable="$(find ~/.cache/browsers -name chrome-headless-shell -type f | head -1)" \
  --concurrency=1
```

`src/data.ts` holds the scene list, timings and copy; it is generated from each
scene's `meta.json` after a capture.

## Small disks

Remotion buffers every rendered frame as an image, which needs a few hundred MB.
On a small machine, render `--muted` in frame ranges and stitch afterwards:

```sh
for i in $(seq 0 18); do
  s=$((i * 80)); e=$((s + 79))
  bunx remotion render src/index.ts Tour /tmp/tour-$i.mp4 \
    --browser-executable="$CHROME" --concurrency=1 --frames=$s-$e --muted
done
printf "file '%s'\n" /tmp/tour-*.mp4 > /tmp/list.txt
ffmpeg -f concat -safe 0 -i /tmp/list.txt -c copy /tmp/tour-silent.mp4
ffmpeg -i /tmp/tour-silent.mp4 -stream_loop -1 -i public/audio/soundtrack.mp3 \
  -map 0:v -map 1:a -t 48.1 -c:v libx264 -crf 20 -pix_fmt yuv420p \
  -c:a aac -b:a 160k -movflags +faststart ../public/promo/oru-product-tour.mp4
```
