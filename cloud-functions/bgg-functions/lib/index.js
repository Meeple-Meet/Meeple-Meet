"use strict";
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
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.searchGames = exports.getGamesByIds = exports.ping = void 0;
exports._clearInMemoryStateForTests = _clearInMemoryStateForTests;
const firebase_functions_1 = require("firebase-functions");
const admin = __importStar(require("firebase-admin"));
const https_1 = require("firebase-functions/v2/https");
const logger = __importStar(require("firebase-functions/logger"));
const node_fetch_1 = __importDefault(require("node-fetch"));
const fast_xml_parser_1 = require("fast-xml-parser");
const Cache_1 = require("./Cache");
// Apply global options to all functions
(0, firebase_functions_1.setGlobalOptions)({
    region: "us-central1", // Deployment region (keep all functions in the same region)
    memory: "256MiB", // Memory allocation (lower = cheaper, 256MiB is enough for lightweight functions)
    timeoutSeconds: 30, // Max execution time per request (prevents long-running costs)
    maxInstances: 5, // Limit concurrent instances (controls scaling and cost)
    minInstances: 0, // Keep 0 warm instances (no cost when idle)
    concurrency: 1, // Number of requests handled per instance (1 = predictable performance)
    cpu: 0.25 // Fraction of CPU allocated (lower = cheaper, sufficient for simple tasks)
});
if (!admin.apps.length) {
    admin.initializeApp();
}
// Test endpoint
exports.ping = (0, https_1.onRequest)((req, res) => {
    res.json({ message: "pong" });
});
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
const gameCache = new Cache_1.Cache(GAME_TTL_MS, GAME_CACHE_MAX, GAME_CACHE_COLLECTION);
const searchCache = new Cache_1.Cache(SEARCH_TTL_MS, SEARCH_CACHE_MAX, SEARCH_CACHE_COLLECTION);
// --------------------
// Cloud Functions
// --------------------
/**
 * getGamesByIds
 *
 * HTTP GET endpoint: expects `ids` query param (comma-separated list or array)
 *
 * Behavior:
 * 1. Parses query parameter and limits to 20 IDs
 * 2. Checks L1 in-memory cache for each ID
 * 3. For missing IDs, checks L2 Firestore cache
 * 4. For still missing IDs, triggers coalesced BGG API fetch
 * 5. Stores successfully fetched games in both L1 and L2 caches
 * 6. Returns ordered array of GameOut (skips missing/failures)
 *
 * Response codes:
 *  - 200: successful array of games
 *  - 400: invalid/missing query parameter
 *  - 502: BGG API fetch failed
 *  - 500: unexpected internal error
 */
exports.getGamesByIds = (0, https_1.onRequest)({ secrets: ["BGG_API_TOKEN"] }, async (req, res) => {
    try {
        const idsParam = req.query.ids;
        if (!idsParam) {
            res.status(400).json({ error: "Missing query param 'ids'" });
            return;
        }
        const rawIds = typeof idsParam === "string"
            ? idsParam.split(",")
            : Array.isArray(idsParam)
                ? idsParam.map(String)
                : [String(idsParam)];
        const ids = rawIds.map((s) => s.trim()).filter(Boolean).slice(0, 20);
        if (!ids.length) {
            res.status(400).json({ error: "No valid ids provided" });
            return;
        }
        const cachedById = {};
        let missingIds = [];
        // Check cache
        for (const id of ids) {
            const cached = await gameCache.get(id);
            if (cached)
                cachedById[id] = cached;
            else
                missingIds.push(id);
        }
        logMsg("getGamesByIds", { requested: ids.length, cached: Object.keys(cachedById).length, missing: missingIds.length });
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
                    }
                    else {
                        logWarning("coalesced fetch returned no game for id", { id });
                    }
                }
                const results = ids.map((id) => { var _a; return (_a = cachedById[id]) !== null && _a !== void 0 ? _a : null; }).filter(Boolean);
                res.json(results);
                return;
            }
            catch (err) {
                logError("Error in coalesced getGamesByIds", {
                    err: err instanceof Error ? { message: err.message, stack: err.stack } : _safeSerialize(err),
                });
                res.status(502).json({ error: "BGG fetch failed", message: err === null || err === void 0 ? void 0 : err.message });
                return;
            }
        }
        else {
            // all cached
            const results = ids.map((id) => cachedById[id]).filter(Boolean);
            res.json(results);
            return;
        }
    }
    catch (err) {
        logError("getGamesByIds unexpected error", { err: err instanceof Error ? { message: err.message, stack: err.stack } : _safeSerialize(err) });
        res.status(500).json({ error: "Internal server error", message: err === null || err === void 0 ? void 0 : err.message });
        return;
    }
});
/**
 * searchGames
 *
 * HTTP GET endpoint: expects `query` param, optional `maxResults` and `ignoreCase`
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
exports.searchGames = (0, https_1.onRequest)({ secrets: ["BGG_API_TOKEN"] }, async (req, res) => {
    var _a, _b;
    try {
        const queryParam = req.query.query;
        if (!queryParam || typeof queryParam !== "string") {
            res.status(400).json({ error: "Missing query parameter 'query'" });
            return;
        }
        const maxResults = Math.min(Number(req.query.maxResults) || 20, 50);
        const ignoreCase = req.query.ignoreCase === "true";
        const cacheKey = `${queryParam.toLowerCase()}:${ignoreCase ? "i" : "s"}:max${maxResults}`;
        // Check cache
        const cached = await searchCache.get(cacheKey);
        if (cached) {
            logMsg("searchGames cache hit", { query: queryParam });
            res.json(cached.slice(0, maxResults));
            return;
        }
        logMsg("searchGames cache miss", { query: queryParam });
        // --- Fetch BGG ---
        const url = `https://boardgamegeek.com/xmlapi2/search?query=${encodeURIComponent(queryParam)}&type=boardgame`;
        logMsg("searchGames: fetching", { url });
        const response = await fetchWithAuth(url);
        if (!response.ok) {
            const text = await response.text().catch(() => "");
            logError("BGG search failed", { status: response.status, body: text });
            res.status(502).json({ error: `BGG API returned ${response.status}`, details: text });
            return;
        }
        const xml = await response.text();
        const parsed = parser.parse(xml);
        let items = (_b = (_a = parsed === null || parsed === void 0 ? void 0 : parsed.items) === null || _a === void 0 ? void 0 : _a.item) !== null && _b !== void 0 ? _b : [];
        if (!Array.isArray(items))
            items = [items];
        const results = items
            .map((item) => {
            try {
                const id = item === null || item === void 0 ? void 0 : item["@id"];
                if (!id)
                    return null;
                let nameNode = item.name;
                if (!nameNode)
                    return null;
                const names = Array.isArray(nameNode) ? nameNode : [nameNode];
                const primary = names.find((n) => n["@type"] === "primary");
                if (!primary)
                    return null;
                const name = primary["@value"];
                if (!name)
                    return null;
                return { id, name };
            }
            catch (_a) {
                return null;
            }
        })
            .filter((r) => r != null);
        const ranked = rankSearchResults(results, queryParam, ignoreCase).slice(0, maxResults);
        // --- Write caches ---
        await searchCache.set(cacheKey, ranked);
        res.json(ranked);
        return;
    }
    catch (err) {
        logError("searchGames unexpected error", { err: err instanceof Error ? { message: err.message, stack: err.stack } : _safeSerialize(err) });
        res.status(500).json({ error: "Internal server error", message: err === null || err === void 0 ? void 0 : err.message });
        return;
    }
});
/**
 * _clearInMemoryStateForTests
 * Utility function for test environment.
 * Clears all in-memory caches and pending batch queues.
 */
function _clearInMemoryStateForTests() {
    try {
        gameCache.clearL1();
        searchCache.clearL1();
        // empty pendingBatches array in-place
        pendingBatches.splice(0, pendingBatches.length);
    }
    catch (e) {
        // never throw in normal code path; tests can log if needed
        logWarning("clearInMemoryStateForTests failed", { err: e });
    }
}
// --------------------
// Pending batch coalescing
// --------------------
const PENDING_DEBOUNCE_MS = process.env.NODE_ENV === "test" ? 0 : 100;
const pendingBatches = [];
/**
 * Batch coalescing logic
 *  - scheduleBatchFetch: adds IDs to pending batch
 *  - Debounce timer triggers batch fetch to BGG API
 *  - Resolves all pending requesters with fetched results
 *  - Ensures batch size <= 20
 */
function scheduleBatchFetch(ids) {
    // normalize and dedupe input ids to be safe
    const uniqueIds = Array.from(new Set(ids.map((s) => String(s).trim()).filter(Boolean)));
    if (uniqueIds.length === 0)
        return Promise.resolve({});
    // we will create one promise per chunk assigned to a pending batch (existing or new)
    const chunkPromises = [];
    // remaining ids to assign
    const remaining = new Set(uniqueIds);
    // First: try to fill existing pending batches as much as possible
    for (const pb of pendingBatches) {
        if (remaining.size === 0)
            break;
        const freeSlots = 20 - pb.ids.size;
        if (freeSlots <= 0)
            continue;
        // take up to freeSlots from remaining
        const toTake = [];
        for (const id of remaining) {
            if (toTake.length >= freeSlots)
                break;
            toTake.push(id);
        }
        if (toTake.length === 0)
            continue;
        // add those ids to the existing pending batch
        toTake.forEach((id) => {
            pb.ids.add(id);
            remaining.delete(id);
        });
        // register a per-chunk promise that will be resolved by that pending batch
        const p = new Promise((resolve, reject) => {
            pb.requesters.push({ requestedIds: toTake, resolve, reject });
        });
        chunkPromises.push(p);
    }
    // Second: for any ids still remaining, create new pending batch(es) (chunks of up to 20)
    while (remaining.size > 0) {
        const take = [];
        for (const id of remaining) {
            if (take.length >= 20)
                break;
            take.push(id);
        }
        // remove from remaining
        take.forEach((id) => remaining.delete(id));
        // create a promise for this chunk and create a new pending batch
        const p = new Promise((resolve, reject) => {
            const pb = {
                ids: new Set(take),
                requesters: [{ requestedIds: take, resolve, reject }],
                timer: setTimeout(async () => {
                    var _a, _b, _c, _d;
                    // remove pb from global list
                    const idx = pendingBatches.indexOf(pb);
                    if (idx >= 0)
                        pendingBatches.splice(idx, 1);
                    const batchIds = Array.from(pb.ids);
                    logMsg("coalesced batch firing", { count: batchIds.length, ids: batchIds });
                    try {
                        const bggUrl = `https://boardgamegeek.com/xmlapi2/thing?id=${batchIds.join(",")}&type=boardgame&stats=1`;
                        const response = await fetchWithAuth(bggUrl);
                        if (!response.ok) {
                            const text = await response.text().catch(() => "");
                            logError("BGG fetch failed (coalesced)", { status: response.status, body: text });
                            for (const r of pb.requesters)
                                r.reject(new Error(`BGG API returned ${response.status}`));
                            return;
                        }
                        const xml = await response.text();
                        const parsed = parser.parse(xml);
                        let items = (_b = (_a = parsed === null || parsed === void 0 ? void 0 : parsed.items) === null || _a === void 0 ? void 0 : _a.item) !== null && _b !== void 0 ? _b : [];
                        if (!Array.isArray(items))
                            items = [items];
                        const fetchedById = {};
                        for (const item of items) {
                            try {
                                const g = parseItemToGame(item);
                                fetchedById[g.uid] = g;
                            }
                            catch (e) {
                                logWarning("Ignored item during parse (coalesced)", {
                                    id: item === null || item === void 0 ? void 0 : item["@id"],
                                    reason: (_c = e === null || e === void 0 ? void 0 : e.message) !== null && _c !== void 0 ? _c : _safeSerialize(e),
                                });
                            }
                        }
                        // resolve each registered requester with only their requestedIds
                        for (const r of pb.requesters) {
                            const resultMap = {};
                            for (const id of r.requestedIds)
                                resultMap[id] = (_d = fetchedById[id]) !== null && _d !== void 0 ? _d : null;
                            r.resolve(resultMap);
                        }
                    }
                    catch (err) {
                        logError("Error during coalesced fetch", {
                            err: err instanceof Error ? { message: err.message, stack: err.stack } : _safeSerialize(err),
                        });
                        for (const r of pb.requesters)
                            r.reject(err);
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
        const merged = {};
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
const parser = new fast_xml_parser_1.XMLParser({
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
function extractText(node) {
    if (node == null)
        return null;
    if (typeof node === "string")
        return node;
    if (typeof node === "object" && node["#text"] != null)
        return String(node["#text"]);
    return null;
}
/**
 * parseItemToGame
 * Converts a parsed XML item into GameOut object
 * Validates required fields (uid, name, imageURL, min/max players)
 * Handles optional fields: recommendedPlayers, averagePlayTime, minAge, genres
 */
function parseItemToGame(item) {
    var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l, _m, _o;
    const uid = (_a = item === null || item === void 0 ? void 0 : item["@id"]) !== null && _a !== void 0 ? _a : null;
    if (!uid)
        throw new Error("missing id");
    // Image
    const image = extractText(item.image);
    if (!image)
        throw new Error("Missing imageURL");
    // Name
    let nameNode = item.name;
    if (!nameNode)
        throw new Error("missing name");
    if (Array.isArray(nameNode)) {
        nameNode = (_b = nameNode.find((n) => (n === null || n === void 0 ? void 0 : n["@type"]) === "primary")) !== null && _b !== void 0 ? _b : nameNode[0];
    }
    const name = (_d = (_c = nameNode === null || nameNode === void 0 ? void 0 : nameNode["@value"]) !== null && _c !== void 0 ? _c : extractText(nameNode)) !== null && _d !== void 0 ? _d : null;
    if (!name)
        throw new Error("missing primary name");
    // Description
    const description = (_e = extractText(item.description)) !== null && _e !== void 0 ? _e : "";
    // min/max players
    const minPlayers = Number((_f = item.minplayers) === null || _f === void 0 ? void 0 : _f["@value"]);
    const maxPlayers = Number((_g = item.maxplayers) === null || _g === void 0 ? void 0 : _g["@value"]);
    if (Number.isNaN(minPlayers) || Number.isNaN(maxPlayers))
        throw new Error("missing players");
    // Optional fields
    // Recommended players
    let recommendedPlayers = null;
    try {
        const pollSummaries = (_j = (_h = item["poll-summary"]) !== null && _h !== void 0 ? _h : item["poll_summary"]) !== null && _j !== void 0 ? _j : item.poll;
        const pollArray = Array.isArray(pollSummaries) ? pollSummaries : [pollSummaries].filter(Boolean);
        const suggested = pollArray.find((p) => (p === null || p === void 0 ? void 0 : p["@name"]) === "suggested_numplayers");
        const results = Array.isArray(suggested === null || suggested === void 0 ? void 0 : suggested.result) ? suggested.result : [suggested === null || suggested === void 0 ? void 0 : suggested.result].filter(Boolean);
        const best = results.find((r) => (r === null || r === void 0 ? void 0 : r["@name"]) === "bestwith");
        const raw = (_k = best === null || best === void 0 ? void 0 : best["@value"]) !== null && _k !== void 0 ? _k : null;
        if (raw != null) {
            const m = String(raw).match(/\d+/);
            recommendedPlayers = m ? Number(m[0]) : null;
        }
    }
    catch (_p) {
        recommendedPlayers = null;
    }
    // Average play time
    const playingTime = item.playingtime ? Number((_l = item.playingtime) === null || _l === void 0 ? void 0 : _l["@value"]) : null;
    const minAge = item.minage ? Number((_m = item.minage) === null || _m === void 0 ? void 0 : _m["@value"]) : null;
    // Genres
    let links = (_o = item.link) !== null && _o !== void 0 ? _o : [];
    if (!Array.isArray(links))
        links = [links];
    const genres = links
        .filter((l) => (l === null || l === void 0 ? void 0 : l["@type"]) === "boardgamecategory")
        .map((l) => l === null || l === void 0 ? void 0 : l["@value"])
        .filter((v) => v != null);
    return {
        uid: String(uid),
        name: String(name),
        description: String(description),
        imageURL: image,
        minPlayers,
        maxPlayers,
        recommendedPlayers,
        averagePlayTime: playingTime !== null && playingTime !== void 0 ? playingTime : null,
        minAge: minAge !== null && minAge !== void 0 ? minAge : null,
        genres,
    };
}
/**
 * Search ranking helpers
 *  - levenshtein: computes edit distance
 *  - rankSearchResults: ranks GameSearchResult[] by exact match, substring match, then Levenshtein distance
 */
function levenshtein(a, b) {
    const dp = Array(a.length + 1)
        .fill(0)
        .map(() => Array(b.length + 1).fill(0));
    for (let i = 0; i <= a.length; i++)
        dp[i][0] = i;
    for (let j = 0; j <= b.length; j++)
        dp[0][j] = j;
    for (let i = 1; i <= a.length; i++) {
        for (let j = 1; j <= b.length; j++) {
            dp[i][j] = Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
        }
    }
    return dp[a.length][b.length];
}
function rankSearchResults(results, query, ignoreCase) {
    const q = ignoreCase ? query.toLowerCase() : query;
    return results.sort((a, b) => {
        const nameA = ignoreCase ? a.name.toLowerCase() : a.name;
        const nameB = ignoreCase ? b.name.toLowerCase() : b.name;
        const exactA = nameA === q ? 0 : 1;
        const exactB = nameB === q ? 0 : 1;
        if (exactA !== exactB)
            return exactA - exactB;
        const idxA = nameA.indexOf(q) >= 0 ? nameA.indexOf(q) : Infinity;
        const idxB = nameB.indexOf(q) >= 0 ? nameB.indexOf(q) : Infinity;
        if (idxA !== idxB)
            return idxA - idxB;
        return levenshtein(nameA, q) - levenshtein(nameB, q);
    });
}
/**
 * Logging helpers
 *  - logMsg / logWarning / logError: serialize metadata safely before logging
 */
function _safeSerialize(value) {
    try {
        if (value instanceof Error) {
            return { message: value.message, stack: value.stack };
        }
        // attempt JSON-safe clone
        return JSON.parse(JSON.stringify(value));
    }
    catch (_a) {
        try {
            return String(value);
        }
        catch (_b) {
            return "[unserializable]";
        }
    }
}
function formatLog(obj) {
    if (!obj)
        return {};
    const out = {};
    for (const k of Object.keys(obj)) {
        out[k] = _safeSerialize(obj[k]);
    }
    return out;
}
function logMsg(message, meta) {
    if (meta)
        logger.log(message, formatLog(meta));
    else
        logger.log(message);
}
function logWarning(message, meta) {
    if (meta)
        logger.warn(message, formatLog(meta));
    else
        logger.warn(message);
}
function logError(message, meta) {
    if (meta)
        logger.error(message, formatLog(meta));
    else
        logger.error(message);
}
/**
 * fetchWithAuth
 * Fetch helper that adds User-Agent header and optional BGG API token authorization
 */
async function fetchWithAuth(url) {
    const token = process.env.BGG_API_TOKEN;
    const headers = { "User-Agent": "MeepleMeet/1.0" };
    if (token)
        headers["Authorization"] = `Bearer ${token}`;
    return (0, node_fetch_1.default)(url, { headers });
}
//# sourceMappingURL=index.js.map