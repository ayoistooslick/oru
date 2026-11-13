// Record the real ORU screens while a scripted person uses them.
//
// Screenshots are taken as fast as Chrome will produce them (~10 fps) for the
// whole of each action, so typing, scrolling and page changes are captured as
// they actually happen. Remotion replays those frames and adds the titles,
// captions, phone frame and score on top.
//
//   node scripts/promo/tour_scenes.cjs
//
// Output: video/public/frames/<scene>/0001.jpg ... plus meta.json (timings).

const fs = require("fs");
const path = require("path");
const puppeteer = require("puppeteer-core");

const SITE = (process.env.SITE || "https://oru.freebuff.app").replace(/\/+$/, "");
const ROOT = path.join(__dirname, "..", "..");
const OUT = path.join(ROOT, "video", "public", "frames");
const TOUR = path.join(ROOT, ".promo", "tour");
const STATE = JSON.parse(fs.readFileSync(path.join(TOUR, "state.json"), "utf8"));
const SONG = "Midnight at the Lagoon";
const PHONE = { width: 360, height: 800, deviceScaleFactor: 2 };
const QUALITY = 78;

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function findChrome() {
  const root = path.join(process.env.HOME || "/home/daytona", ".cache", "browsers");
  const found = [];
  (function walk(dir) {
    for (const name of fs.readdirSync(dir)) {
      const full = path.join(dir, name);
      if (fs.statSync(full).isDirectory()) walk(full);
      else if (name === "chrome-headless-shell" || name === "chrome") found.push(full);
    }
  })(root);
  return found[0];
}

async function login(email, password) {
  const response = await fetch(`${SITE}/api?__path=/api/v1/auth/login`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  if (!response.ok) throw new Error(`login ${response.status}`);
  return response.json();
}

async function openPage(browser, auth) {
  const context = await browser.createBrowserContext();
  const page = await context.newPage();
  await page.setViewport(PHONE);
  await page.evaluateOnNewDocument(
    (auth) => {
      localStorage.setItem("oru.tutorial.v1", "done");
      localStorage.setItem("oru.device-setup-dismissed", "1");
      if (auth) {
        localStorage.setItem("oru.token", auth.access_token);
        localStorage.setItem("oru.user", JSON.stringify(auth.user));
      }
    },
    auth || null,
  );
  return page;
}

async function record(page, name, action) {
  const dir = path.join(OUT, name);
  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(dir, { recursive: true });

  let stop = false;
  let count = 0;
  const times = [];
  const started = Date.now();

  const loop = (async () => {
    while (!stop) {
      const at = Date.now() - started;
      try {
        const buffer = await page.screenshot({ type: "jpeg", quality: QUALITY });
        count += 1;
        fs.writeFileSync(path.join(dir, `${String(count).padStart(4, "0")}.jpg`), buffer);
        times.push(at);
      } catch (error) {
        break;
      }
    }
  })();

  await action();
  stop = true;
  await loop;

  const duration = (times[times.length - 1] || 0) / 1000;
  fs.writeFileSync(
    path.join(dir, "meta.json"),
    JSON.stringify({ frames: count, duration, times }, null, 1),
  );
  console.log(`${name.padEnd(11)} ${String(count).padStart(4)} frames  ${duration.toFixed(1)}s`);
  return duration;
}

async function scroll(page, delta, steps = 6, pause = 110) {
  for (let i = 0; i < steps; i += 1) {
    await page.mouse.wheel({ deltaY: delta / steps });
    await sleep(pause);
  }
}

async function main() {
  const artistAuth = await login(STATE.email, STATE.password);
  const listenerAuth = await login(STATE.listener.email, STATE.listener.password);
  console.log("logged in as", artistAuth.user.display_name, "and", listenerAuth.user.display_name);

  const browser = await puppeteer.launch({
    executablePath: findChrome(),
    headless: "shell",
    args: ["--no-sandbox", "--hide-scrollbars", "--disable-dev-shm-usage"],
    defaultViewport: PHONE,
  });

  const only = process.argv[2];
  const scenes = {};
  const run = async (name, auth, start, action, wait = 900) => {
    if (only && only !== name) return;
    const page = await openPage(browser, auth);
    await page.goto(`${SITE}${start}`, { waitUntil: "networkidle0" });
    await sleep(wait);
    scenes[name] = await record(page, name, () => action(page));
    await page.close();
  };

  // 1. Sign-up: open the form, fill it, send it.
  const email = `tour${Date.now().toString(36)}@example.com`;
  await run("signup", null, "/auth", async (page) => {
    await sleep(700);
    await page.evaluate(() => {
      [...document.querySelectorAll("button")]
        .find((node) => node.textContent.trim() === "Create account")
        ?.click();
    });
    await sleep(900);
    await page.type('input[placeholder="you@example.com"]', email, { delay: 55 });
    await sleep(180);
    await page.type('input[placeholder="Your name"]', "Kandy Waves", { delay: 65 });
    await sleep(180);
    await page.type('input[placeholder="your_handle"]', "kandywaves", { delay: 60 });
    await sleep(180);
    await page.type('input[placeholder="At least 10 characters"]', "sentinel-oru-tour-8842", {
      delay: 45,
    });
    await sleep(700);
    await page.evaluate(() => {
      const form = document.querySelector("form");
      form?.requestSubmit();
    });
    await sleep(3200);
  });

  // 2. The studio, and the upload form arriving.
  await run("studio", artistAuth, "/studio", async (page) => {
    await sleep(600);
    await scroll(page, 260, 8, 130);
    await sleep(700);
    await scroll(page, 240, 7, 130);
    await sleep(800);
  });

  // 3. Filling a release in: real files, real typing.
  await run("upload", artistAuth, "/studio", async (page) => {
    await page.waitForSelector('input[placeholder="Song title"]', { timeout: 20000 });
    await page.evaluate(() => {
      [...document.querySelectorAll("h2")]
        .find((node) => node.textContent.trim() === "Upload a song")
        ?.scrollIntoView({ block: "center" });
    });
    await sleep(900);
    const audio = await page.$('input[type="file"][accept^="audio"]');
    const cover = await page.$('input[type="file"][accept^="image"]');
    await audio.uploadFile(path.join(TOUR, "midnight-at-the-lagoon.wav"));
    await sleep(900);
    await cover.uploadFile(path.join(TOUR, "midnight-at-the-lagoon.jpg"));
    await sleep(700);
    await page.click('input[placeholder="Song title"]');
    await page.type('input[placeholder="Song title"]', SONG, { delay: 70 });
    await sleep(300);
    await page.click('input[placeholder="afrobeats, rap, gospel"]');
    await page.type('input[placeholder="afrobeats, rap, gospel"]', "afrobeats, soul", { delay: 60 });
    await sleep(1100);
  });

  // 4. The release already live in the studio.
  await run("published", artistAuth, "/studio", async (page) => {
    await page.evaluate(() => {
      const heading = [...document.querySelectorAll("h2")].find((node) =>
        node.textContent.trim().startsWith("Your songs"),
      );
      heading?.scrollIntoView({ block: "start" });
    });
    await sleep(1300);
    await scroll(page, 140, 5, 140);
    await sleep(700);
  });

  // 5. A listener finding it in the feed, then opening it.
  await run("discover", listenerAuth, "/listen", async (page) => {
    await sleep(500);
    await scroll(page, 300, 7, 140);
    await sleep(700);
    await page.evaluate((song) => {
      document.querySelector(`[aria-label="Play ${song}"]`)?.click();
    }, SONG);
    await sleep(1600);
  });

  // 6. Liking it from the now-playing screen.
  await run("like", listenerAuth, "/listen", async (page) => {
    await page.evaluate((song) => {
      document.querySelector(`[aria-label="Play ${song}"]`)?.click();
    }, SONG);
    await sleep(1800);
    await page.evaluate(() => {
      const button = document.querySelector('[aria-label="Add to likes"]');
      if (button) button.click();
      else
        document
          .querySelector('[aria-label^="Remove"][aria-label$="from likes"]')
          ?.setAttribute("data-liked", "1");
    });
    await sleep(1400);
  });

  // 7. Back in the studio: the numbers that changed.
  await run("stats", artistAuth, "/studio", async (page) => {
    await sleep(700);
    await scroll(page, 180, 5, 140);
    await sleep(900);
  });

  // 8. The public artist page.
  await run("artist", artistAuth, `/artist/${STATE.artistId || 5}`, async (page) => {
    await sleep(700);
    await scroll(page, 240, 6, 140);
    await sleep(700);
  });

  await browser.close();
  const meta = {};
  for (const [name, duration] of Object.entries(scenes)) meta[name] = duration;
  fs.writeFileSync(path.join(OUT, "summary.json"), JSON.stringify(meta, null, 1));
  console.log("captured", Object.keys(scenes).join(", "));
}

main().catch((error) => {
  console.error("ERR", error.message);
  process.exit(1);
});
