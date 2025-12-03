/**
 * Import function triggers from their respective submodules:
 *
 * import {onCall} from "firebase-functions/v2/https";
 * import {onDocumentWritten} from "firebase-functions/v2/firestore";
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

import { setGlobalOptions } from "firebase-functions";
import { onRequest } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";
import fetch from "node-fetch";
import { XMLParser } from "fast-xml-parser";

setGlobalOptions({ maxInstances: 10 });

// Test endpoint
export const ping = onRequest((req, res) => {
  res.json({ message: "pong" });
});

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
  let recommendedPlayers: number | null = null;
  try {
    const pollSummaries = item["poll-summary"] ?? item.poll;
    if (pollSummaries) {
      const pollArray = Array.isArray(pollSummaries) ? pollSummaries : [pollSummaries];
      const suggested = pollArray.find((p: any) => p?.["@name"] === "suggested_numplayers");
      const results = suggested?.result
        ? Array.isArray(suggested.result)
          ? suggested.result
          : [suggested.result]
        : [];
      const best = results.find((r: any) => r?.["@name"] === "bestwith");
      const raw = best?.["@value"] ?? null;
      if (raw != null) {
        const m = String(raw).match(/\d+/);
        recommendedPlayers = m ? Number(m[0]) : null;
      }
    }
  } catch {
    recommendedPlayers = null;
  }

  const playingTime = item.playingtime ? Number(item.playingtime?.["@value"]) : null;
  const minAge = item.minage ? Number(item.minage?.["@value"]) : null;

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
  const token = process.env.BGG_API_TOKEN
  const headers: Record<string, string> = { "User-Agent": "MeepleMeet/1.0" };

  if (token) headers["Authorization"] = `Bearer ${token}`;

  return fetch(url, { headers });
}

// --------------------
// Cloud Functions
// --------------------
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
      if (ids.length === 0) {
        res.status(400).json({ error: "No valid ids provided" });
        return;
      }

      const bggUrl = `https://boardgamegeek.com/xmlapi2/thing?id=${ids.join(
        ","
      )}&type=boardgame&stats=1`;

      logger.log("getGamesByIds: fetching", { bggUrl, count: ids.length });

      const response = await fetchWithAuth(bggUrl);
      if (!response.ok) {
        const text = await response.text().catch(() => "");
        logger.error("BGG fetch failed", { status: response.status, body: text });
        res.status(502).json({ error: `BGG API returned ${response.status}`, details: text });
        return;
      }

      const xml = await response.text();
      const parsed = parser.parse(xml);

      let items = parsed?.items?.item ?? [];
      if (!Array.isArray(items)) items = [items];

      const games: GameOut[] = [];
      for (const item of items) {
        try {
          const g = parseItemToGame(item);
          games.push(g);
        } catch (e: any) {
          logger.warn("Ignored item during parse", { id: item?.["@id"], reason: e?.message });
        }
      }

      res.json(games);
    } catch (err: any) {
      logger.error("getGamesByIds unexpected error", { err: err?.message ?? err });
      res.status(500).json({ error: "Internal server error", message: err?.message });
    }
});

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

      const url = `https://boardgamegeek.com/xmlapi2/search?query=${encodeURIComponent(
        queryParam
      )}&type=boardgame`;

      logger.log("searchGames: fetching", { url });

      const response = await fetchWithAuth(url);
      if (!response.ok) {
        const text = await response.text().catch(() => "");
        logger.error("BGG search failed", { status: response.status, body: text });
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
            if (Array.isArray(nameNode))
              nameNode = nameNode.find((n: any) => n["@type"] === "primary") ?? nameNode[0];
            const name = nameNode?.["@value"] ?? null;
            if (!name) return null;

            return { id, name };
          } catch {
            return null;
          }
        })
        .filter((r: GameSearchResult | null): r is GameSearchResult => r != null);

      const ranked = rankSearchResults(results, queryParam, ignoreCase).slice(0, maxResults);

      res.json(ranked);
    } catch (err: any) {
      logger.error("searchGames unexpected error", { err: err?.message ?? err });
      res.status(500).json({ error: "Internal server error", message: err?.message });
    }
});
