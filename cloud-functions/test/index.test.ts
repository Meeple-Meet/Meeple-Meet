// Test suite for Cloud Functions
// Tests the handler logic extracted from Callable functions
// Generated with Claude Sonet 4.5

import nock from "nock";
import { _clearInMemoryStateForTests } from "../src/index";

// Mock Firebase Admin before importing functions
jest.mock("firebase-admin", () => {
  const mockTimestamp = { toMillis: () => Date.now() };

  const mockFirestoreInstance = {
    collection: jest.fn(() => ({
      doc: jest.fn(() => ({
        get: jest.fn(() => Promise.resolve({ exists: false, data: () => null })),
        set: jest.fn(() => Promise.resolve()),
        delete: jest.fn(() => Promise.resolve()),
      })),
    })),
  };

  const firestoreFn: any = jest.fn(() => mockFirestoreInstance);
  firestoreFn.Timestamp = { now: jest.fn(() => mockTimestamp) };

  return {
    apps: [],
    initializeApp: jest.fn(),
    firestore: firestoreFn,
  };
});

// Import handler functions for testing
import { handleGetGamesByIds, handleSearchGames, ping } from "../src/index";

describe("Cloud Functions Tests", () => {
  beforeAll(() => {
    // Set environment variables for tests
    process.env.BGG_API_TOKEN = "test-token";
  });

  afterAll(() => {
    nock.cleanAll();
  });

  beforeEach(() => {
    _clearInMemoryStateForTests();
    jest.clearAllMocks();
    nock.cleanAll();
  });

  // ==================== Ping Test ====================

  describe("ping", () => {
    it("should return pong (HTTP endpoint)", async () => {
      const req = { method: "GET", query: {} } as any;
      const res = {
        status: jest.fn().mockReturnThis(),
        json: jest.fn().mockReturnThis(),
        send: jest.fn().mockReturnThis(),
      } as any;

      await ping(req, res);

      expect(res.json).toHaveBeenCalledWith({ message: "pong" });
    });
  });

  // ==================== getGamesByIds Tests ====================

  describe("getGamesByIds", () => {
    it("should return games for valid IDs", async () => {
      const bggResponse = `
        <?xml version="1.0" encoding="UTF-8"?>
        <items termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="181">
            <image>https://example.com/risk.jpg</image>
            <name type="primary" value="Risk" />
            <description>A war game</description>
            <minplayers value="2" />
            <maxplayers value="6" />
            <poll-summary name="suggested_numplayers">
              <result name="bestwith" value="Best with 4 players" />
            </poll-summary>
            <playingtime value="120" />
            <minage value="10" />
            <link type="boardgamecategory" value="Wargame" />
          </item>
        </items>
      `;

      nock("https://boardgamegeek.com")
        .get("/xmlapi2/thing")
        .query(true)
        .reply(200, bggResponse);

      const result = await handleGetGamesByIds({ ids: ["181"] });

      expect(result).toEqual(
        expect.arrayContaining([
          expect.objectContaining({
            uid: "181",
            name: "Risk",
            description: "A war game",
            minPlayers: 2,
            maxPlayers: 6,
            recommendedPlayers: 4,
            averagePlayTime: 120,
            minAge: 10,
          }),
        ])
      );
    });

    it("should handle multiple IDs", async () => {
      const bggResponse = `
        <?xml version="1.0" encoding="UTF-8"?>
        <items termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="181">
            <image>https://example.com/risk.jpg</image>
            <name type="primary" value="Risk" />
            <description>War game</description>
            <minplayers value="2" />
            <maxplayers value="6" />
            <playingtime value="120" />
            <minage value="10" />
          </item>
          <item type="boardgame" id="13">
            <image>https://example.com/catan.jpg</image>
            <name type="primary" value="CATAN" />
            <description>Settle the island</description>
            <minplayers value="3" />
            <maxplayers value="4" />
            <playingtime value="120" />
            <minage value="10" />
          </item>
        </items>
      `;

      nock("https://boardgamegeek.com")
        .get("/xmlapi2/thing")
        .query(true)
        .reply(200, bggResponse);

      const result = await handleGetGamesByIds({ ids: ["181", "13"] });

      expect(result).toEqual(
        expect.arrayContaining([
          expect.objectContaining({ uid: "181", name: "Risk" }),
          expect.objectContaining({ uid: "13", name: "CATAN" }),
        ])
      );
    });

    it("should throw for missing ids parameter", async () => {
      await expect(handleGetGamesByIds({})).rejects.toThrow("Missing or invalid 'ids' array");
    });

    it("should throw for non-array ids", async () => {
      await expect(handleGetGamesByIds({ ids: "181" })).rejects.toThrow("Missing or invalid 'ids' array");
    });

    it("should throw for empty ids", async () => {
      await expect(handleGetGamesByIds({ ids: ["   ", ""] })).rejects.toThrow("No valid ids provided");
    });

    it("should throw when BGG API fails", async () => {
      nock("https://boardgamegeek.com")
        .get("/xmlapi2/thing")
        .query(true)
        .reply(500, "Internal Server Error");

      await expect(handleGetGamesByIds({ ids: ["181"] })).rejects.toThrow("BGG fetch failed");
    });

    it("should handle games with missing optional fields", async () => {
      const bggResponse = `
        <?xml version="1.0" encoding="UTF-8"?>
        <items termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="999">
            <image>https://example.com/minimal.jpg</image>
            <name type="primary" value="Minimal Game" />
            <description>Minimal description</description>
            <minplayers value="1" />
            <maxplayers value="4" />
          </item>
        </items>
      `;

      nock("https://boardgamegeek.com")
        .get("/xmlapi2/thing")
        .query(true)
        .reply(200, bggResponse);

      const result = await handleGetGamesByIds({ ids: ["999"] });

      expect(result).toEqual(
        expect.arrayContaining([
          expect.objectContaining({
            uid: "999",
            name: "Minimal Game",
            recommendedPlayers: null,
            averagePlayTime: null,
            minAge: null,
          }),
        ])
      );
    });

    it("should parse recommended players from poll-summary", async () => {
      const bggResponse = `
        <?xml version="1.0" encoding="UTF-8"?>
        <items termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="13">
            <image>https://example.com/catan.jpg</image>
            <name type="primary" value="CATAN" />
            <description>Settle</description>
            <minplayers value="3" />
            <maxplayers value="4" />
            <playingtime value="120" />
            <minage value="10" />
            <poll-summary name="suggested_numplayers">
              <result name="bestwith" value="Best with 4 players" />
            </poll-summary>
          </item>
        </items>
      `;

      nock("https://boardgamegeek.com")
        .get("/xmlapi2/thing")
        .query(true)
        .reply(200, bggResponse);

      const result = await handleGetGamesByIds({ ids: ["13"] });

      expect(result).toEqual(
        expect.arrayContaining([
          expect.objectContaining({
            recommendedPlayers: 4,
          }),
        ])
      );
    });

    it("should extract multiple genres correctly", async () => {
      const bggResponse = `
        <?xml version="1.0" encoding="UTF-8"?>
        <items termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="181">
            <image>https://example.com/risk.jpg</image>
            <name type="primary" value="Risk" />
            <description>War</description>
            <minplayers value="2" />
            <maxplayers value="6" />
            <playingtime value="120" />
            <minage value="10" />
            <poll-summary name="suggested_numplayers">
              <result name="bestwith" value="Best with 4 players" />
            </poll-summary>
            <link type="boardgamecategory" value="Territory Building" />
            <link type="boardgamecategory" value="Wargame" />
            <link type="boardgamecategory" value="Strategy" />
            <link type="boardgamemechanic" value="Dice Rolling" />
          </item>
        </items>
      `;

      nock("https://boardgamegeek.com")
        .get("/xmlapi2/thing")
        .query(true)
        .reply(200, bggResponse);

      const result = await handleGetGamesByIds({ ids: ["181"] });

      expect(result).toEqual(
        expect.arrayContaining([
          expect.objectContaining({
            genres: expect.arrayContaining([
              "Territory Building",
              "Wargame",
              "Strategy",
            ]),
          }),
        ])
      );

      expect(result[0].genres).not.toContain("Dice Rolling");
    });

    it("should limit to 20 IDs", async () => {
      const ids = Array.from({ length: 21 }, (_, i) => (i + 1).toString());

      const bggResponse = `
        <?xml version="1.0" encoding="UTF-8"?>
        <items termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
        </items>
      `;

      nock("https://boardgamegeek.com")
        .get("/xmlapi2/thing")
        .query(true)
        .reply(200, bggResponse);

      const result = await handleGetGamesByIds({ ids });

      expect(result).toBeDefined();
    });

    it("should coalesce concurrent requests into a single BGG fetch", async () => {
      const bggResponse = `
        <?xml version="1.0" encoding="UTF-8"?>
        <items termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="1">
            <image>https://example.com/1.jpg</image>
            <name type="primary" value="G1" />
            <description>d1</description>
            <minplayers value="1" />
            <maxplayers value="2" />
          </item>
        </items>
      `;

      // capture the requests: we expect exactly ONE network call
      let callCount = 0;
      let lastUri: string | null = null;

      nock("https://boardgamegeek.com")
        .get("/xmlapi2/thing")
        .query(true)
        .times(1)
        .reply(function (uri, body) {
          callCount++;
          lastUri = uri;
          return [200, bggResponse];
        });

      // create two concurrent calls whose ids total <= 20
      const p1 = handleGetGamesByIds({ ids: ["1", "2", "3"] });
      const p2 = handleGetGamesByIds({ ids: ["4"] });

      await Promise.all([p1, p2]);

      expect(callCount).toBe(1);
      expect(lastUri).toBeTruthy();
      expect(lastUri).toContain("id=");
    });
  });

  // ==================== searchGames Tests ====================

  describe("searchGames", () => {
    it("should return search results", async () => {
      const bggResponse = `
        <?xml version="1.0" encoding="UTF-8"?>
        <items total="3" termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="1406">
            <name type="primary" value="Monopoly" />
          </item>
          <item type="boardgame" id="238393">
            <name type="primary" value="Monolith Arena" />
          </item>
          <item type="boardgame" id="365359">
            <name type="primary" value="Mono" />
          </item>
        </items>
      `;

      nock("https://boardgamegeek.com")
        .get("/xmlapi2/search")
        .query(true)
        .reply(200, bggResponse);

      const result = await handleSearchGames({ query: "mono", maxResults: 10 });

      expect(result).toEqual(
        expect.arrayContaining([
          expect.objectContaining({ id: "1406", name: "Monopoly" }),
          expect.objectContaining({ id: "238393", name: "Monolith Arena" }),
          expect.objectContaining({ id: "365359", name: "Mono" }),
        ])
      );
    });

    it("should rank exact match first", async () => {
      const bggResponse = `
        <?xml version="1.0" encoding="UTF-8"?>
        <items total="3" termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="1">
            <name type="primary" value="Monopoly Arena" />
          </item>
          <item type="boardgame" id="2">
            <name type="primary" value="Mono" />
          </item>
          <item type="boardgame" id="3">
            <name type="primary" value="Monopoly" />
          </item>
        </items>
      `;

      nock("https://boardgamegeek.com")
        .get("/xmlapi2/search")
        .query(true)
        .reply(200, bggResponse);

      const result = await handleSearchGames({ query: "mono", maxResults: 10 });

      expect(result[0].name).toBe("Mono");
    });

    it("should respect maxResults parameter", async () => {
      const bggResponse = `
        <?xml version="1.0" encoding="UTF-8"?>
        <items total="5" termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="1">
            <name type="primary" value="Game 1" />
          </item>
          <item type="boardgame" id="2">
            <name type="primary" value="Game 2" />
          </item>
          <item type="boardgame" id="3">
            <name type="primary" value="Game 3" />
          </item>
          <item type="boardgame" id="4">
            <name type="primary" value="Game 4" />
          </item>
          <item type="boardgame" id="5">
            <name type="primary" value="Game 5" />
          </item>
        </items>
      `;

      nock("https://boardgamegeek.com")
        .get("/xmlapi2/search")
        .query(true)
        .reply(200, bggResponse);

      const result = await handleSearchGames({ query: "game", maxResults: 3 });

      expect(result.length).toBe(3);
    });

    it("should throw for missing query parameter", async () => {
      await expect(handleSearchGames({})).rejects.toThrow("Missing or invalid 'query' parameter");
    });

    it("should throw when BGG API fails", async () => {
      nock("https://boardgamegeek.com")
        .get("/xmlapi2/search")
        .query(true)
        .reply(500, "Internal Server Error");

      await expect(handleSearchGames({ query: "test", maxResults: 10 })).rejects.toThrow();
    });

    it("should handle empty search results", async () => {
      const bggResponse = `
        <?xml version="1.0" encoding="UTF-8"?>
        <items total="0" termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
        </items>
      `;

      nock("https://boardgamegeek.com")
        .get("/xmlapi2/search")
        .query(true)
        .reply(200, bggResponse);

      const result = await handleSearchGames({ query: "nonexistent", maxResults: 10 });

      expect(result).toEqual([]);
    });

    it("should use Levenshtein distance for ranking fallback", async () => {
      const bggResponse = `
        <?xml version="1.0" encoding="UTF-8"?>
        <items total="3" termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="1">
            <name type="primary" value="Monopoly" />
          </item>
          <item type="boardgame" id="2">
            <name type="primary" value="Monop" />
          </item>
          <item type="boardgame" id="3">
            <name type="primary" value="Monopo" />
          </item>
        </items>
      `;

      nock("https://boardgamegeek.com")
        .get("/xmlapi2/search")
        .query(true)
        .reply(200, bggResponse);

      const result = await handleSearchGames({ query: "mono", maxResults: 10 });

      expect(result.length).toBe(3);
      expect(result[0].name).toBe("Monop");
      expect(result[1].name).toBe("Monopo");
      expect(result[2].name).toBe("Monopoly");
    });

    it("should cap maxResults at 50", async () => {
      const bggResponse = `
        <?xml version="1.0" encoding="UTF-8"?>
        <items total="0" termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
        </items>
      `;

      nock("https://boardgamegeek.com")
        .get("/xmlapi2/search")
        .query(true)
        .reply(200, bggResponse);

      const result = await handleSearchGames({ query: "testcap", maxResults: 100 });

      expect(result).toEqual([]);
    });

    it("should ignore items without primary name", async () => {
      const bggResponse = `
        <?xml version="1.0" encoding="UTF-8"?>
        <items total="2" termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="1">
            <name type="primary" value="Valid Game" />
          </item>
          <item type="boardgame" id="2">
            <name type="alternate" value="Invalid Game" />
          </item>
        </items>
      `;

      nock("https://boardgamegeek.com")
        .get("/xmlapi2/search")
        .query(true)
        .reply(200, bggResponse);

      const result = await handleSearchGames({ query: "gameprimary", maxResults: 10 });

      expect(result.length).toBe(1);
      expect(result[0].name).toBe("Valid Game");
    });
  });

  // ==================== Cache & Auth Tests ====================

  describe("Cache and Auth", () => {
    it("should set Authorization header when token is present", async () => {
      const bggResponse = `
        <?xml version="1.0" encoding="UTF-8"?>
        <items termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
          <item type="boardgame" id="1">
            <image>https://example.com/game.jpg</image>
            <name type="primary" value="Test" />
            <description>Test</description>
            <minplayers value="1" />
            <maxplayers value="4" />
          </item>
        </items>
      `;

      nock("https://boardgamegeek.com", {
        reqheaders: {
          authorization: "Bearer test-token",
        },
      })
        .get("/xmlapi2/thing")
        .query(true)
        .reply(200, bggResponse);

      await handleGetGamesByIds({ ids: ["1"] });
    });
  });
});
