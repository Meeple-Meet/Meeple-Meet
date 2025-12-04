/**
 * Import function triggers from their respective submodules:
 *
 * import {onCall} from "firebase-functions/v2/https";
 * import {onDocumentWritten} from "firebase-functions/v2/firestore";
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

import { setGlobalOptions } from "firebase-functions";
import * as admin from "firebase-admin";
import { onRequest } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";
import fetch from "node-fetch";
import { XMLParser } from "fast-xml-parser";

setGlobalOptions({ maxInstances: 10 });

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();

// Test endpoint
export const ping = onRequest((req, res) => {
  res.json({ message: "pong" });
});

// -----------------------
// Safe logging helpers
// -----------------------
function _safeSerialize(value: any): any {
  try {
    if (value instanceof Error) {
      return { message: value.message, stack: value.stack };
    }
    // attempt JSON-safe clone
    return JSON.parse(JSON.stringify(value));
  } catch {
    try {
      return String(value);
    } catch {
      return "[unserializable]";
    }
  }
}

function formatLog(obj: Record<string, any> | undefined) {
  if (!obj) return {};
  const out: Record<string, any> = {};
  for (const k of Object.keys(obj)) {
    out[k] = _safeSerialize(obj[k]);
  }
  return out;
}

function safeLog(message: string, meta?: Record<string, any>) {
  if (meta) logger.log(message, formatLog(meta));
  else logger.log(message);
}
function safeWarn(message: string, meta?: Record<string, any>) {
  if (meta) logger.warn(message, formatLog(meta));
  else logger.warn(message);
}
function safeError(message: string, meta?: Record<string, any>) {
  if (meta) logger.error(message, formatLog(meta));
  else logger.error(message);
}

// --------------------
// Cache config
// --------------------
const GAME_TTL_MS = 24 * 60 * 60 * 1000;
const SEARCH_TTL_MS = 24 * 60 * 60 * 1000;
const GAME_CACHE_MAX = 1000;
const SEARCH_CACHE_MAX = 100;
const GAME_CACHE_COLLECTION = "gameCache";
const SEARCH_CACHE_COLLECTION = "searchCache";

// Simple L1 in-memory caches (non-persistent)
type CacheEntry<T> = { value: T; expireAt: number };
const gameCache = new Map<string, CacheEntry<any>>();
const searchCache = new Map<string, CacheEntry<any>>();

function getFromCache<T>(map: Map<string, CacheEntry<T>>, key: string): T | null {
  const e = map.get(key);
  if (!e) return null;
  if (Date.now() > e.expireAt) {
    map.delete(key);
    return null;
  }
  return e.value;
}

function setToCache<T>(
  map: Map<string, CacheEntry<T>>,
  key: string,
  value: T,
  ttlMs: number,
  maxSize: number
) {
  map.set(key, { value, expireAt: Date.now() + ttlMs });
  // LRU eviction
  while (map.size > maxSize) {
    const firstKey = map.keys().next().value;
    if (!firstKey) break;
    map.delete(firstKey);
  }
}

// --------------------
// L2 cache in-database (persistent)
// --------------------
async function getFromL2Cache<T>(collection: string, key: string, ttlMs: number): Promise<T | null> {
  try {
    const docRef = db.collection(collection).doc(key);
    const doc = await docRef.get();
    if (!doc.exists) return null;
    const data = doc.data();
    if (!data || data.value == null || data.updatedAt == null) return null;

    // support different updatedAt formats: number(ms), Firestore Timestamp (toMillis), Date object
    let updatedAtMs: number;
    const ua = data.updatedAt;
    if (typeof ua === "number") {
      updatedAtMs = ua;
    } else if (ua && typeof ua.toMillis === "function") {
      updatedAtMs = ua.toMillis();
    } else if (ua && typeof ua.toDate === "function") {
      updatedAtMs = ua.toDate().getTime();
    } else {
      updatedAtMs = Date.now();
    }

    if (Date.now() - updatedAtMs > ttlMs) {
      try {
        await docRef.delete();
      } catch (e) {
        safeWarn("Failed to delete expired L2 cache doc", { collection, key, err: e });
      }
      return null;
    }
    return data.value as T;
  } catch (err) {
    safeWarn("getFromL2Cache failed", { collection, key, err });
    return null;
  }
}

async function setToL2Cache<T>(collection: string, key: string, value: T) {
  const docRef = db.collection(collection).doc(key);
  try {
    await docRef.set({
      value,
      updatedAt: Date.now(),
    });
  } catch (err) {
    safeWarn("setToL2Cache failed", { collection, key, err });
  }
}

// --------------------
// Pending batch coalescing
// --------------------
const PENDING_DEBOUNCE_MS = process.env.NODE_ENV === "test" ? 0 : 100;

type PendingRequester = {
  requestedIds: string[];
  resolve: (map: Record<string, GameOut | null>) => void;
  reject: (err: any) => void;
};

type PendingBatch = {
  ids: Set<string>;
  requesters: PendingRequester[];
  timer: NodeJS.Timeout;
};

const pendingBatches: PendingBatch[] = [];

function scheduleBatchFetch(ids: string[]): Promise<Record<string, GameOut | null>> {
  return new Promise((resolve, reject) => {
    for (const pb of pendingBatches) {
      const combined = new Set([...pb.ids, ...ids]);
      if (combined.size <= 20) {
        ids.forEach((id) => pb.ids.add(id));
        pb.requesters.push({ requestedIds: ids, resolve, reject });
        return;
      }
    }

    const pb: PendingBatch = {
      ids: new Set(ids),
      requesters: [{ requestedIds: ids, resolve, reject }],
      timer: setTimeout(async () => {
        const idx = pendingBatches.indexOf(pb);
        if (idx >= 0) pendingBatches.splice(idx, 1);

        const batchIds = Array.from(pb.ids);
        safeLog("coalesced batch firing", { count: batchIds.length, ids: batchIds });

        try {
          const bggUrl = `https://boardgamegeek.com/xmlapi2/thing?id=${batchIds.join(",")}&type=boardgame&stats=1`;
          const response = await fetchWithAuth(bggUrl);

          if (!response.ok) {
            const text = await response.text().catch(() => "");
            safeError("BGG fetch failed (coalesced)", { status: response.status, body: text });
            for (const r of pb.requesters) r.reject(new Error(`BGG API returned ${response.status}`));
            return;
          }

          const xml = await response.text();
          const parsed = parser.parse(xml);
          let items = parsed?.items?.item ?? [];
          if (!Array.isArray(items)) items = [items];

          const fetchedById: Record<string, GameOut> = {};
          for (const item of items) {
            try {
              const g = parseItemToGame(item);
              fetchedById[g.uid] = g;
            } catch (e: any) {
              safeWarn("Ignored item during parse (coalesced)", { id: item?.["@id"], reason: e?.message ?? _safeSerialize(e) });
            }
          }

          for (const r of pb.requesters) {
            const resultMap: Record<string, GameOut | null> = {};
            for (const id of r.requestedIds) resultMap[id] = fetchedById[id] ?? null;
            r.resolve(resultMap);
          }
        } catch (err: any) {
          safeError("Error during coalesced fetch", {
            err: err instanceof Error ? { message: err.message, stack: err.stack } : _safeSerialize(err),
          });
          for (const r of pb.requesters) r.reject(err);
        }
      }, PENDING_DEBOUNCE_MS),
    };

    pendingBatches.push(pb);
  });
}

// --------------------
// BGG Types
// --------------------
type GameOut = {
  uid: string;
  name: string;
  description: string;
  imageURL: string;
  minPlayers: number;
  maxPlayers: number;
  recommendedPlayers?: number | null;
  averagePlayTime?: number | null;
  minAge?: number | null;
  genres: string[];
};

type GameSearchResult = {
  id: string;
  name: string;
};

// --------------------
// XML Parser
// --------------------
const parser = new XMLParser({
  ignoreAttributes: false,
  attributeNamePrefix: "@",
  textNodeName: "#text",
  ignoreDeclaration: true,
  removeNSPrefix: true,
  isArray: (tagName) => tagName === "link",
});

// --------------------
// Helpers
// --------------------
function safeText(node: any): string | null {
  if (node == null) return null;
  if (typeof node === "string") return node;
  if (typeof node === "object" && node["#text"] != null) return String(node["#text"]);
  return null;
}

/**
 * Build a GameOut from a parsed XML item.
 */
function parseItemToGame(item: any): GameOut {
  const uid = item?.["@id"] ?? null;
  if (!uid) throw new Error("missing id");

  // Image
  const thumbnail = safeText(item.thumbnail);
  if (!thumbnail) throw new Error("Missing imageURL");

  // Name
  let nameNode = item.name;
  if (!nameNode) throw new Error("missing name");
  if (Array.isArray(nameNode)) {
    nameNode = nameNode.find((n: any) => n?.["@type"] === "primary") ?? nameNode[0];
  }
  const name = nameNode?.["@value"] ?? safeText(nameNode) ?? null;
  if (!name) throw new Error("missing primary name");

  // Description
  const description = safeText(item.description) ?? "";

  // min/max players
  const minPlayers = Number(item.minplayers?.["@value"]);
  const maxPlayers = Number(item.maxplayers?.["@value"]);
  if (Number.isNaN(minPlayers) || Number.isNaN(maxPlayers)) throw new Error("missing players");

  // Optional fields

  // Recommended players
  let recommendedPlayers: number | null = null;
  try {
    const pollSummaries = item["poll-summary"] ?? item["poll_summary"] ?? item.poll;
    const pollArray = Array.isArray(pollSummaries) ? pollSummaries : [pollSummaries].filter(Boolean);
    const suggested = pollArray.find((p: any) => p?.["@name"] === "suggested_numplayers");
    const results = Array.isArray(suggested?.result) ? suggested.result : [suggested?.result].filter(Boolean);
    const best = results.find((r: any) => r?.["@name"] === "bestwith");
    const raw = best?.["@value"] ?? null;
    if (raw != null) {
      const m = String(raw).match(/\d+/);
      recommendedPlayers = m ? Number(m[0]) : null;
    }
  } catch {
    recommendedPlayers = null;
  }

  // Average play time
  const playingTime = item.playingtime ? Number(item.playingtime?.["@value"]) : null;
  const minAge = item.minage ? Number(item.minage?.["@value"]) : null;

  // Genres
  let links = item.link ?? [];
  if (!Array.isArray(links)) links = [links];
  const genres = links
    .filter((l: any) => l?.["@type"] === "boardgamecategory")
    .map((l: any) => l?.["@value"])
    .filter((v: any) => v != null);

  return {
    uid: String(uid),
    name: String(name),
    description: String(description),
    imageURL: thumbnail,
    minPlayers,
    maxPlayers,
    recommendedPlayers,
    averagePlayTime: playingTime ?? null,
    minAge: minAge ?? null,
    genres,
  };
}

// --------------------
// Search utils
// --------------------
function levenshtein(a: string, b: string): number {
  const dp: number[][] = Array(a.length + 1)
    .fill(0)
    .map(() => Array(b.length + 1).fill(0));

  for (let i = 0; i <= a.length; i++) dp[i][0] = i;
  for (let j = 0; j <= b.length; j++) dp[0][j] = j;

  for (let i = 1; i <= a.length; i++) {
    for (let j = 1; j <= b.length; j++) {
      dp[i][j] = Math.min(
        dp[i - 1][j] + 1,
        dp[i][j - 1] + 1,
        dp[i - 1][j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1)
      );
    }
  }
  return dp[a.length][b.length];
}

function rankSearchResults(
  results: GameSearchResult[],
  query: string,
  ignoreCase: boolean
): GameSearchResult[] {
  const q = ignoreCase ? query.toLowerCase() : query;

  return results.sort((a, b) => {
    const nameA = ignoreCase ? a.name.toLowerCase() : a.name;
    const nameB = ignoreCase ? b.name.toLowerCase() : b.name;

    const exactA = nameA === q ? 0 : 1;
    const exactB = nameB === q ? 0 : 1;
    if (exactA !== exactB) return exactA - exactB;

    const idxA = nameA.indexOf(q) >= 0 ? nameA.indexOf(q) : Infinity;
    const idxB = nameB.indexOf(q) >= 0 ? nameB.indexOf(q) : Infinity;
    if (idxA !== idxB) return idxA - idxB;

    return levenshtein(nameA, q) - levenshtein(nameB, q);
  });
}

// --------------------
// Fetch helper with Authorization
// --------------------
async function fetchWithAuth(url: string) {
  const token = process.env.BGG_API_TOKEN;
  const headers: Record<string, string> = { "User-Agent": "MeepleMeet/1.0" };

  if (token) headers["Authorization"] = `Bearer ${token}`;

  return fetch(url, { headers });
}

// --------------------
// Cloud Functions
// --------------------

/**
 * getGamesByIds
 * Query: ids=1,2,3
 * Behaviour:
 *  - check L1 cache per id
 *  - if some ids missing -> check L2 cache
 *  - if still missing -> single BGG call for missing ids
 *  - parse returned items, store each parsed game in L1 + L2 cache
 *  - return ordered array of games (only successfully parsed ones)
 */
export const getGamesByIds = onRequest(
  { secrets: ["BGG_API_TOKEN"] },
  async (req, res) => {
    try {
      const idsParam = req.query.ids;
      if (!idsParam) {
        res.status(400).json({ error: "Missing query param 'ids'" });
        return;
      }

      const rawIds: string[] =
        typeof idsParam === "string"
          ? idsParam.split(",")
          : Array.isArray(idsParam)
          ? idsParam.map(String)
          : [String(idsParam)];

      const ids = rawIds.map((s) => s.trim()).filter(Boolean).slice(0, 20);
      if (!ids.length) {
        res.status(400).json({ error: "No valid ids provided" });
        return;
      }

      const cachedById: Record<string, GameOut> = {};
      let missingIds: string[] = [];

      // Check L1 cache
      for (const id of ids) {
        const cached = getFromCache<GameOut>(gameCache, `game:${id}`);
        if (cached) cachedById[id] = cached;
        else missingIds.push(id);
      }

      // Check L2 cache
      if (missingIds.length > 0) {
        const l2Results = await Promise.all(
          missingIds.map((id) => getFromL2Cache<GameOut>(GAME_CACHE_COLLECTION, id, GAME_TTL_MS))
        );

        for (let i = 0; i < l2Results.length; i++) {
          const g = l2Results[i];
          if (g) {
            cachedById[g.uid] = g;
            setToCache(gameCache, `game:${g.uid}`, g, GAME_TTL_MS, GAME_CACHE_MAX); // update L1
          }
        }

        // Recompute missingIds after L2 processing
        missingIds = ids.filter((id) => !cachedById[id]);
      }

      safeLog("getGamesByIds", { requested: ids.length, cached: Object.keys(cachedById).length, missing: missingIds.length });

      // Fetch from BGG if still missing
      if (missingIds.length > 0) {
        safeLog("getGamesByIds: scheduling coalesced fetch", { missingIdsCount: missingIds.length, missingIds });

        try {
          const fetchedMap = await scheduleBatchFetch(missingIds);

          for (const id of Object.keys(fetchedMap)) {
            const g = fetchedMap[id];
            if (g) {
              setToCache(gameCache, `game:${g.uid}`, g, GAME_TTL_MS, GAME_CACHE_MAX);
              await setToL2Cache(GAME_CACHE_COLLECTION, g.uid, g).catch((e) =>
                safeWarn("setToL2Cache rejected", { collection: GAME_CACHE_COLLECTION, key: g.uid, err: e })
              );
              cachedById[g.uid] = g;
            } else {
              safeWarn("coalesced fetch returned no game for id", { id });
            }
          }

          const ordered = ids.map((id) => cachedById[id] ?? null).filter(Boolean);
          res.json(ordered);
          return;
        } catch (err: any) {
          safeError("Error in coalesced getGamesByIds", {
            err: err instanceof Error ? { message: err.message, stack: err.stack } : _safeSerialize(err),
          });
          res.status(502).json({ error: "BGG fetch failed", message: err?.message });
          return;
        }
      } else {
        // all cached
        const ordered = ids.map((id) => cachedById[id]).filter(Boolean);
        res.json(ordered);
        return;
      }
    } catch (err: any) {
      safeError("getGamesByIds unexpected error", { err: err instanceof Error ? { message: err.message, stack: err.stack } : _safeSerialize(err) });
      res.status(500).json({ error: "Internal server error", message: err?.message });
      return;
    }
  }
);

/**
 * searchGames
 * Query: query=..., maxResults=..., ignoreCase=true|false
 * Behaviour:
 *  - check L1 cache for query key (query normalized + ignoreCase + maxResults)
 *  - if miss -> check L2 cache
 *  - if still miss -> fetch BGG search, parse, rank, store in caches
 */
export const searchGames = onRequest(
  { secrets: ["BGG_API_TOKEN"] },
  async (req, res) => {
    try {
      const queryParam = req.query.query;
      if (!queryParam || typeof queryParam !== "string") {
        res.status(400).json({ error: "Missing query parameter 'query'" });
        return;
      }

      const maxResults = Math.min(Number(req.query.maxResults) || 20, 50);
      const ignoreCase = req.query.ignoreCase === "true";

      const cacheKey = `search:${queryParam.toLowerCase()}:${ignoreCase ? "i" : "s"}:max${maxResults}`;

      // Check L1 cache
      let cached = getFromCache<GameSearchResult[]>(searchCache, cacheKey);

      // Check L2 cache if miss
      if (!cached) {
        cached = await getFromL2Cache<GameSearchResult[]>(SEARCH_CACHE_COLLECTION, cacheKey, SEARCH_TTL_MS);
        if (cached) {
          safeLog("searchGames cache hit (L2)", { query: queryParam, cachedCount: cached.length });
          setToCache(searchCache, cacheKey, cached, SEARCH_TTL_MS, SEARCH_CACHE_MAX);
        }
      }

      if (cached) {
        safeLog("searchGames cache hit (L1/L2)", { query: queryParam });
        res.json(cached.slice(0, maxResults));
        return;
      }

      safeLog("searchGames cache miss", { query: queryParam });

      // --- Fetch BGG ---
      const url = `https://boardgamegeek.com/xmlapi2/search?query=${encodeURIComponent(queryParam)}&type=boardgame`;
      safeLog("searchGames: fetching", { url });

      const response = await fetchWithAuth(url);
      if (!response.ok) {
        const text = await response.text().catch(() => "");
        safeError("BGG search failed", { status: response.status, body: text });
        res.status(502).json({ error: `BGG API returned ${response.status}`, details: text });
        return;
      }

      const xml = await response.text();
      const parsed = parser.parse(xml);
      let items = parsed?.items?.item ?? [];
      if (!Array.isArray(items)) items = [items];

      const results: GameSearchResult[] = items
        .map((item: any) => {
          try {
            const id = item?.["@id"];
            if (!id) return null;

            let nameNode = item.name;
            if (!nameNode) return null;

            const names = Array.isArray(nameNode) ? nameNode : [nameNode];
            const primary = names.find((n: any) => n["@type"] === "primary");
            if (!primary) return null;

            const name = primary["@value"];
            if (!name) return null;

            return { id, name };
          } catch {
            return null;
          }
        })
        .filter((r: GameSearchResult | null): r is GameSearchResult => r != null);

      const ranked = rankSearchResults(results, queryParam, ignoreCase).slice(0, maxResults);

      // --- Write caches ---
      setToCache(searchCache, cacheKey, ranked, SEARCH_TTL_MS, SEARCH_CACHE_MAX);
      await setToL2Cache(SEARCH_CACHE_COLLECTION, cacheKey, ranked);

      res.json(ranked);
      return;
    } catch (err: any) {
      safeError("searchGames unexpected error", { err: err instanceof Error ? { message: err.message, stack: err.stack } : _safeSerialize(err) });
      res.status(500).json({ error: "Internal server error", message: err?.message });
      return;
    }
  }
);


// Exported helper for tests: clear in-memory caches + pending batches
export function _clearInMemoryStateForTests() {
  try {
    gameCache.clear();
    searchCache.clear();
    // empty pendingBatches array in-place
    pendingBatches.splice(0, pendingBatches.length);
  } catch (e) {
    // never throw in normal code path; tests can log if needed
    safeWarn("clearInMemoryStateForTests failed", { err: e });
  }
}

