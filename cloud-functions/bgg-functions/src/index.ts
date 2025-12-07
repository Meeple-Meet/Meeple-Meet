/**
 * Firebase Cloud Functions for BoardGameGeek (BGG) API.
 *
 * Provides endpoints to:
 *  - Fetch detailed game info by IDs (`getGamesByIds`)
 *  - Search games by query (`searchGames`)
 *
 * Implements:
 *  - L1 in-memory cache (fast, non-persistent)
 *  - L2 Firestore cache (persistent)
 *  - Batch coalescing to reduce BGG API calls
 *
 * Utilities:
 *  - XML parsing for BGG API responses
 *  - Ranking search results using Levenshtein distance
 *  - Safe logging helpers
 *
 * Notes:
 *  - Maximum of 20 IDs per batch (BGG API limit)
 *  - Pending batches are debounced to coalesce multiple simultaneous requests
 *  - Errors are logged but generally do not crash the function
 */

import { setGlobalOptions } from "firebase-functions";
import * as admin from "firebase-admin";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";
import fetch from "node-fetch";
import { XMLParser } from "fast-xml-parser";
import { Cache } from "./Cache"

// Apply global options to all functions
setGlobalOptions({
  region: "us-central1",   // Deployment region (keep all functions in the same region)
  memory: "256MiB",        // Memory allocation (lower = cheaper, 256MiB is enough for lightweight functions)
  timeoutSeconds: 30,      // Max execution time per request (prevents long-running costs)
  maxInstances: 5,         // Limit concurrent instances (controls scaling and cost)
  minInstances: 0,         // Keep 0 warm instances (no cost when idle)
  concurrency: 1,          // Number of requests handled per instance (1 = predictable performance)
  cpu: 0.25                // Fraction of CPU allocated (lower = cheaper, sufficient for simple tasks)
});

if (!admin.apps.length) {
  admin.initializeApp();
}

// Test endpoint (keep as HTTP for easy testing)
import { onRequest } from "firebase-functions/v2/https";
export const ping = onRequest((req, res) => {
  res.json({ message: "pong" });
});

// --------------------
// BGG Types
// --------------------

/**
 * GameOut
 * Represents a fully parsed board game object from BGG.
 *
 * Fields:
 *  - uid: BGG game ID
 *  - name: primary game name
 *  - description: game description text
 *  - imageURL: Image URL
 *  - minPlayers/maxPlayers: player count range
 *  - recommendedPlayers: suggested number of players (optional)
 *  - averagePlayTime: average game duration in minutes (optional)
 *  - minAge: recommended minimum age (optional)
 *  - genres: array of genre strings
 */
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

/**
 * GameSearchResult
 * Represents a lightweight search result from BGG.
 * Only contains game ID and name.
 */
type GameSearchResult = {
  id: string;
  name: string;
};

// --------------------
// Cache config
// --------------------
const GAME_TTL_MS = 24 * 60 * 60 * 1000;
const SEARCH_TTL_MS = 24 * 60 * 60 * 1000;
const GAME_CACHE_MAX = 1000;
const SEARCH_CACHE_MAX = 100;
const GAME_CACHE_COLLECTION = "gameCache";
const SEARCH_CACHE_COLLECTION = "searchCache";

// Create cache instance
const gameCache = new Cache<GameOut>(GAME_TTL_MS, GAME_CACHE_MAX, GAME_CACHE_COLLECTION);
const searchCache = new Cache<GameSearchResult[]>(SEARCH_TTL_MS, SEARCH_CACHE_MAX, SEARCH_CACHE_COLLECTION);

// --------------------
// Cloud Functions
// --------------------

/**
 * getGamesByIds
 *
 * Callable Function: expects data.ids (array of strings)
 *
 * Behavior:
 * 1. Parses data parameter and limits to 20 IDs
 * 2. Checks L1 in-memory cache for each ID
 * 3. For missing IDs, checks L2 Firestore cache
 * 4. For still missing IDs, triggers coalesced BGG API fetch
 * 5. Stores successfully fetched games in both L1 and L2 caches
 * 6. Returns ordered array of GameOut (skips missing/failures)
 *
 * Throws:
 *  - invalid-argument: invalid/missing data parameter
 *  - unavailable: BGG API fetch failed
 *  - internal: unexpected internal error
 */
/**
 * Handler function for getGamesByIds - extracted for testing
 */
export async function handleGetGamesByIds(data: any): Promise<GameOut[]> {
  try {
    const { ids } = data;

      if (!ids || !Array.isArray(ids)) {
        throw new HttpsError("invalid-argument", "Missing or invalid 'ids' array");
      }

      const validIds = ids.map((id) => String(id).trim()).filter(Boolean).slice(0, 20);

      if (validIds.length === 0) {
        throw new HttpsError("invalid-argument", "No valid ids provided");
      }

      const cachedById: Record<string, GameOut> = {};
      let missingIds: string[] = [];

      // Check cache
      for (const id of validIds) {
        const cached = await gameCache.get(id);
        if (cached) cachedById[id] = cached;
        else missingIds.push(id);
      }

      logMsg("getGamesByIds", { requested: validIds.length, cached: Object.keys(cachedById).length, missing: missingIds.length });

      // Fetch from BGG if still missing
      if (missingIds.length > 0) {
        logMsg("getGamesByIds: scheduling coalesced fetch", { missingIdsCount: missingIds.length, missingIds });

        try {
          const fetchedMap = await scheduleBatchFetch(missingIds);

          for (const id of Object.keys(fetchedMap)) {
            const g = fetchedMap[id];
            if (g) {
              await gameCache.set(g.uid, g);
              cachedById[g.uid] = g;
            } else {
              logWarning("coalesced fetch returned no game for id", { id });
            }
          }

          const results = validIds.map((id) => cachedById[id] ?? null).filter(Boolean);
          return results;
        } catch (err: any) {
          logError("Error in coalesced getGamesByIds", {
            err: err instanceof Error ? { message: err.message, stack: err.stack } : _safeSerialize(err),
          });
          throw new HttpsError("unavailable", "BGG fetch failed", err?.message);
        }
      } else {
        // all cached
        const results = validIds.map((id) => cachedById[id]).filter(Boolean);
        return results;
      }
    } catch (err: any) {
      if (err instanceof HttpsError) throw err;

      logError("getGamesByIds unexpected error", {
        err: err instanceof Error ? { message: err.message, stack: err.stack } : _safeSerialize(err)
      });
      throw new HttpsError("internal", "Internal server error", err?.message);
    }
}

/**
 * Callable wrapper for getGamesByIds
 */
export const getGamesByIds = onCall(
  {
    secrets: ["BGG_API_TOKEN"],
    enforceAppCheck: true
  },
  async (request) => handleGetGamesByIds(request.data)
);

/**
 * searchGames
 *
 * Callable Function: expects data.query (string), optional data.maxResults (number)
 *
 * Behavior:
 * 1. Checks L1 cache using normalized query key
 * 2. If miss, checks L2 Firestore cache
 * 3. If still miss, fetches BGG search API
 * 4. Parses XML to extract items
 * 5. Ranks results using Levenshtein distance & query position
 * 6. Stores ranked results in L1 and L2 caches
 * 7. Returns array of GameSearchResult (limited by maxResults)
 */
/**
 * Handler function for searchGames - extracted for testing
 */
export async function handleSearchGames(data: any): Promise<GameSearchResult[]> {
  try {
    const { query, maxResults: maxResultsParam } = data;

      if (!query || typeof query !== "string") {
        throw new HttpsError("invalid-argument", "Missing or invalid 'query' parameter");
      }

      const maxResults = Math.min(Number(maxResultsParam) || 20, 50);
      const ignoreCase = true;

      const cacheKey = `${query.toLowerCase()}:i:max${maxResults}`;

      // Check cache
      const cached = await searchCache.get(cacheKey);
      if (cached) {
        logMsg("searchGames cache hit", { query });
        return cached.slice(0, maxResults);
      }

      logMsg("searchGames cache miss", { query });

      // --- Fetch BGG ---
      const url = `https://boardgamegeek.com/xmlapi2/search?query=${encodeURIComponent(query)}&type=boardgame`;
      logMsg("searchGames: fetching", { url });

      const response = await fetchWithAuth(url);
      if (!response.ok) {
        const text = await response.text().catch(() => "");
        logError("BGG search failed", { status: response.status, body: text });
        throw new HttpsError("unavailable", `BGG API returned ${response.status}`, text);
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

      const ranked = rankSearchResults(results, query, ignoreCase).slice(0, maxResults);

      // --- Write caches ---
      await searchCache.set(cacheKey, ranked);

      return ranked;
    } catch (err: any) {
      if (err instanceof HttpsError) throw err;

      logError("searchGames unexpected error", {
        err: err instanceof Error ? { message: err.message, stack: err.stack } : _safeSerialize(err)
      });
      throw new HttpsError("internal", "Internal server error", err?.message);
    }
  }

/**
 * Callable wrapper for searchGames
 */
export const searchGames = onCall(
  {
    secrets: ["BGG_API_TOKEN"],
    enforceAppCheck: true
  },
  async (request) => handleSearchGames(request.data)
);


/**
 * _clearInMemoryStateForTests
 * Utility function for test environment.
 * Clears all in-memory caches and pending batch queues.
 */
export function _clearInMemoryStateForTests() {
  try {
    gameCache.clearL1();
    searchCache.clearL1();
    // empty pendingBatches array in-place
    pendingBatches.splice(0, pendingBatches.length);
  } catch (e) {
    // never throw in normal code path; tests can log if needed
    logWarning("clearInMemoryStateForTests failed", { err: e });
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

/**
 * Batch coalescing logic
 *  - scheduleBatchFetch: adds IDs to pending batch
 *  - Debounce timer triggers batch fetch to BGG API
 *  - Resolves all pending requesters with fetched results
 *  - Ensures batch size <= 20
 */
function scheduleBatchFetch(ids: string[]): Promise<Record<string, GameOut | null>> {
  // normalize and dedupe input ids to be safe
  const uniqueIds = Array.from(new Set(ids.map((s) => String(s).trim()).filter(Boolean)));
  if (uniqueIds.length === 0) return Promise.resolve({});

  // we will create one promise per chunk assigned to a pending batch (existing or new)
  const chunkPromises: Promise<Record<string, GameOut | null>>[] = [];

  // remaining ids to assign
  const remaining = new Set(uniqueIds);

  // First: try to fill existing pending batches as much as possible
  for (const pb of pendingBatches) {
    if (remaining.size === 0) break;
    const freeSlots = 20 - pb.ids.size;
    if (freeSlots <= 0) continue;

    // take up to freeSlots from remaining
    const toTake: string[] = [];
    for (const id of remaining) {
      if (toTake.length >= freeSlots) break;
      toTake.push(id);
    }
    if (toTake.length === 0) continue;

    // add those ids to the existing pending batch
    toTake.forEach((id) => {
      pb.ids.add(id);
      remaining.delete(id);
    });

    // register a per-chunk promise that will be resolved by that pending batch
    const p = new Promise<Record<string, GameOut | null>>((resolve, reject) => {
      pb.requesters.push({ requestedIds: toTake, resolve, reject });
    });
    chunkPromises.push(p);
  }

  // Second: for any ids still remaining, create new pending batch(es) (chunks of up to 20)
  while (remaining.size > 0) {
    const take: string[] = [];
    for (const id of remaining) {
      if (take.length >= 20) break;
      take.push(id);
    }
    // remove from remaining
    take.forEach((id) => remaining.delete(id));

    // create a promise for this chunk and create a new pending batch
    const p = new Promise<Record<string, GameOut | null>>((resolve, reject) => {
      const pb: PendingBatch = {
        ids: new Set(take),
        requesters: [{ requestedIds: take, resolve, reject }],
        timer: setTimeout(async () => {
          // remove pb from global list
          const idx = pendingBatches.indexOf(pb);
          if (idx >= 0) pendingBatches.splice(idx, 1);

          const batchIds = Array.from(pb.ids);
          logMsg("coalesced batch firing", { count: batchIds.length, ids: batchIds });

          try {
            const bggUrl = `https://boardgamegeek.com/xmlapi2/thing?id=${batchIds.join(",")}&type=boardgame&stats=1`;
            const response = await fetchWithAuth(bggUrl);

            if (!response.ok) {
              const text = await response.text().catch(() => "");
              logError("BGG fetch failed (coalesced)", { status: response.status, body: text });
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
                logWarning("Ignored item during parse (coalesced)", {
                  id: item?.["@id"],
                  reason: e?.message ?? _safeSerialize(e),
                });
              }
            }

            // resolve each registered requester with only their requestedIds
            for (const r of pb.requesters) {
              const resultMap: Record<string, GameOut | null> = {};
              for (const id of r.requestedIds) resultMap[id] = fetchedById[id] ?? null;
              r.resolve(resultMap);
            }
          } catch (err: any) {
            logError("Error during coalesced fetch", {
              err: err instanceof Error ? { message: err.message, stack: err.stack } : _safeSerialize(err),
            });
            for (const r of pb.requesters) r.reject(err);
          }
        }, PENDING_DEBOUNCE_MS),
      };

      // push new pb to the global list
      pendingBatches.push(pb);
    });

    chunkPromises.push(p);
  }

  // Finally: wait all chunk promises and merge maps into a single map for the caller
  return Promise.all(chunkPromises).then((maps) => {
    const merged: Record<string, GameOut | null> = {};
    for (const m of maps) {
      for (const k of Object.keys(m)) {
        merged[k] = m[k];
      }
    }
    return merged;
  });
}

/**
 * XML parser configuration for BGG responses
 */
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
function extractText(node: any): string | null {
  if (node == null) return null;
  if (typeof node === "string") return node;
  if (typeof node === "object" && node["#text"] != null) return String(node["#text"]);
  return null;
}

/**
 * parseItemToGame
 * Converts a parsed XML item into GameOut object
 * Validates required fields (uid, name, imageURL, min/max players)
 * Handles optional fields: recommendedPlayers, averagePlayTime, minAge, genres
 */
function parseItemToGame(item: any): GameOut {
  const uid = item?.["@id"] ?? null;
  if (!uid) throw new Error("missing id");

  // Image
  const image = extractText(item.image);
  if (!image) throw new Error("Missing imageURL");

  // Name
  let nameNode = item.name;
  if (!nameNode) throw new Error("missing name");
  if (Array.isArray(nameNode)) {
    nameNode = nameNode.find((n: any) => n?.["@type"] === "primary") ?? nameNode[0];
  }
  const name = nameNode?.["@value"] ?? extractText(nameNode) ?? null;
  if (!name) throw new Error("missing primary name");

  // Description
  const description = extractText(item.description) ?? "";

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
    imageURL: image,
    minPlayers,
    maxPlayers,
    recommendedPlayers,
    averagePlayTime: playingTime ?? null,
    minAge: minAge ?? null,
    genres,
  };
}

/**
 * Search ranking helpers
 *  - levenshtein: computes edit distance
 *  - rankSearchResults: ranks GameSearchResult[] by exact match, substring match, then Levenshtein distance
 */
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

/**
 * Logging helpers
 *  - logMsg / logWarning / logError: serialize metadata safely before logging
 */
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

function logMsg(message: string, meta?: Record<string, any>) {
  if (meta) logger.log(message, formatLog(meta));
  else logger.log(message);
}
function logWarning(message: string, meta?: Record<string, any>) {
  if (meta) logger.warn(message, formatLog(meta));
  else logger.warn(message);
}
function logError(message: string, meta?: Record<string, any>) {
  if (meta) logger.error(message, formatLog(meta));
  else logger.error(message);
}

/**
 * fetchWithAuth
 * Fetch helper that adds User-Agent header and optional BGG API token authorization
 */
async function fetchWithAuth(url: string) {
  const token = process.env.BGG_API_TOKEN;
  const headers: Record<string, string> = { "User-Agent": "MeepleMeet/1.0" };

  if (token) headers["Authorization"] = `Bearer ${token}`;

  return fetch(url, { headers });
}

