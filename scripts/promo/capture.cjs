// Capture real Oru screens for the promo build.
//
// Registers two listener accounts and one artist account against the live
// site, signs in through the app's own token storage, and photographs the
// pages the film needs. Run with:
//
//   node scripts/promo/capture.js
//
// Env: ORU_SCREEN_DIR (default .promo/screens), CHROME (auto-found).

const fs = require("fs");
const path = require("path");
const mkdirp = (dir) => fs.mkdirSync(dir, { recursive: true });
const O = {
  recursive: true,
};

const puppeteer = require("puppeteer-core");

const SITE = "https://oru.freebuff.app";
const API_ENTRY = SITE + "/api?__path=/api/v1";

const SCREEN_DIR = process.env.ORU_SCREEN_DIR || path.join(__dirname, "..", "..", ".promo", "screens");
const PHONE = { width: 412, height: 915, deviceScaleFactor: 2 };
const DESK = { width: 1440, height: 900, deviceScaleFactor: 1 };

function findChrome() {
  if (process.env.CHROME) return process.env.CHROME;
  const root = path.join(process.env.HOME || "/home/daytona", ".cache", "browsers");
  const found = [];
  (function walk(dir) {
    for (const name of fs.readdirSync(dir)) {
      const p = path.join(dir, name);
      const stat = fs.statSync(p);
      if (stat.isDirectory()) walk(p);
      else if (name === "chrome-headless-shell") found.push(p);
    }
  })(root);
  return found[0];
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function signup(page, email, password, handle) {
  return page.evaluate(async (url, email, password, handle) => {
    const response = await fetch(url, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ email, password, handle, display_name: handle }),
    });
    return response.json();
  }, `${API_ENTRY}/auth/register`, email, password, handle);
}

async function makeArtist(page) {
  return page.evaluate(async (url) => {
    const response = await fetch(url, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ stage_name: "Kandy Waves" }),
    });
    return response.json();
  }, `${API_ENTRY}/artists/me`);
}

async function signIn(page, token, user) {
  await page.evaluateOnNewDocument(
    ([tok, usr]) => {
      localStorage.setItem("oru.token", tok);
      localStorage.setItem("oru.user", JSON.stringify(usr));
      localStorage.setItem("oru.tutorial", "done");
    },
    [token, typeof user === "string" ? user : JSON.stringify(user)]
  );
}

async function gotoSettled(page, url, settle = 2600) {
  await page.goto(url, { waitUntil: "networkidle0", timeout: 60000 }).catch(() => {});
  await page.evaluate(() => document.fonts.ready).catch(() => {});
  await sleep(settle);
}

async function shoot(page, name) {
  const file = path.join(SCREEN_DIR, name + ".png");
  await page.screenshot({ path: file });
  console.log("shot", name);
}

async function discoverArtist(page) {
  // The performer profile featured on the front page, so the film shows a
  // artist that actually exists.
  return page.evaluate(async (url) => {
    const response = await fetch(url);
    return response.json();
  }, `${API_ENTRY}/artists?limit=1`);
}

async function likeFirstSong(page) {
  return page.evaluate(async (url) => {
    const songs = await (await fetch(url)).json();
    const id = songs.items?.[0]?.id;
    if (!id) return null;
    const liked = await fetch(`${url}/${id}/likes`, { method: "POST" });
    return { id, title: songs.items[0].title, status: liked.status };
  }, `${API_ENTRY}/songs`);
}

async function createPlaylist(page, name) {
  return page.evaluate(async (url, name) => {
    return (await fetch(url, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ name }),
    })).json();
  }, `${API_ENTRY}/playlists`, name);
}

async function shareExternal(page, title, artistName, url, note) {
  return page.evaluate(async (endpoint, body) => {
    return (await fetch(endpoint, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(body),
    })).json();
  }, `${API_ENTRY}/shared/external`, { title, artist_name: artistName, url, note });
}

async function newContext(browser, viewport) {
  const page = await browser.newPage();
  await page.setViewport(viewport);
  return page;
}

async function main() {
  mkdirp(SCREEN_DIR);
  const browser = await puppeteer.launch({
    executablePath: findChrome(),
    args: ["--no-sandbox", "--disable-dev-shm-usage", "--hide-scrollbars", "--force-device-scale-factor=2"],
    defaultViewport: PHONE,
  });

  const stamp = Date.now().toString(36);
  const emailA = `wavelen${stamp}@example.com`;
  const emailC = `kandywaves${stamp}@example.com`;
  const pass = "sentinel-oru-demo-8842";

  const lisA = await newContext(browser, PHONE);
  await gotoSettled(lisA, SITE + "/");

  // Accounts -----------------------------------------------------------
  const regA = await signup(lisA, emailA, pass, "wavelen");
  const artistUser = await signup(lisA, emailC, pass, "kandywaves");
  await makeArtist(lisA);
  tokenArtist = artistUser.access_token;
  await signIn(lisA, artistUser.access_token, JSON.stringify(artistUser.user ?? artistUser));

  tokenA = regA.access_token;
  await signIn(lisA, tokenA, regA.user ?? regA);

  // Discover a real performer, then like one of their songs so likes and library look lived in.
  const artistList = await discoverArtist(lisA);
  const artist = (artistList.items ?? artistList)[0];
  const liked = await likeFirstSong(lisA);

  // Listener screens ---------------------------------------------------
  await gotoSettled(lisA, SITE + "/listen", 3200);
  await shoot(lisA, "listen_phone");

  const searchPage = await newContext(browser, PHONE);
  await signIn(searchPage, tokenA, regA.user ?? regA);
  await gotoSettled(searchPage, SITE + "/search");
  await searchPage.type("input", "kandy", { delay: 40 }).catch(() => {});
  await sleep(1600);
  await shoot(searchPage, "search_phone");

  const libPage = await newContext(browser, PHONE);
  await signIn(libPage, tokenA, regA.user ?? regA);
  await gotoSettled(libPage, SITE + "/library");
  await shoot(libPage, "library_phone");

  // Artist screens -----------------------------------------------------
  const artistPage = await newContext(browser, DESK);
  await gotoSettled(artistPage, SITE + "/artist/" + artist.id, 3200);
  await shoot(artistPage, "artist_desk");

  const releasePage = await newContext(browser, DESK);
  await gotoSettled(releasePage, SITE + "/release/" + artist.id, 3200).catch(() => {});
  await releasePage.goto(SITE + "/", { waitUntil: "networkidle0" });
  // Real release ids come from the artist page, not a guess.
  const releaseId = await artistPage.evaluate(async (site, id) => {
    const response = await fetch(`${site}/api?__path=${encodeURIComponent("/api/v1/artists/" + id + "/releases?limit=1")}`);
    const body = await response.json();
    return body.items?.[0]?.id ?? null;
  }, SITE, artist.id);
  if (releaseId) {
    await gotoSettled(releasePage, SITE + "/release/" + releaseId, 3200);
    await shoot(releasePage, "release_desk");
  }

  const studioPage = await newContext(browser, DESK);
  await signIn(studioPage, tokenArtist, artistUser.user ?? userPlaceholder(artistUser));
  await gotoSettled(studioPage, SITE + "/studio", 3400);
  await shoot(studioPage, "studio_desk");

  // Social screens -----------------------------------------------------
  await shareExternal(lisA, "Midnight at the Lagoon", "Kandy Waves", "https://soundcloud.com/kandywaves/midnight-at-the-lagoon", "Heard this on the bus and could not stop replaying it. Give it a spin, you will like it too.");
  const socialPage = await newContext(browser, DESK);
  await signIn(socialPage, tokenArtist, artistUser.user ?? userPlaceholder(artistUser));
  await gotoSettled(socialPage, SITE + "/share", 3400);
  const noteBox = await socialPage.$("textarea");
  if (noteBox) await socialPage.type("textarea", "Heard this on the bus and could not stop replaying it.", { delay: 18 }).catch(() => {});
  await shoot(socialPage, "share_desk");
  await socialPage.evaluate(() => document.getElementById("share-feed")?.scrollIntoView({ behavior: "instant", block: "start" })).catch(() => {});
  await socialPage.evaluate(() => window.scrollBy(0, 700)).catch(() => {});
  await sleep(900);
  await shoot(socialPage, "share_feed_desk");

  const inboxPage = await newContext(browser, DESK);
  await signIn(inboxPage, tokenArtist, artistUser.user ?? userPlaceholder(artistUser));
  await gotoSettled(inboxPage, SITE + "/shares", 3400);
  await shoot(inboxPage, "shares_inbox_desk");

  // The public landing page: the moment the link opens on a fresh phone.
  const cleanContext = await newContext(browser, PHONE);
  await gotoSettled(cleanContext, SITE + "/", 3600);
  await shoot(cleanContext, "landing_phone");

  const cleanDesk = await newContext(browser, DESK);
  await gotoSettled(cleanDesk, SITE + "/", 3400);
  await shoot(cleanDesk, "landing_desk");

  await browser.close();
  console.log("done;", liked && liked.status);
}

function userPlaceholder(payload) {
  if (payload && typeof payload === "object") return payload.user ?? payload;
  return payload;
}

let tokenA;
let tokenArtist;

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
