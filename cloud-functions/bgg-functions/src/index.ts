/**
 * Import function triggers from their respective submodules:
 *
 * import {onCall} from "firebase-functions/v2/https";
 * import {onDocumentWritten} from "firebase-functions/v2/firestore";
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

import {setGlobalOptions} from "firebase-functions";
import {onRequest} from "firebase-functions/v2/https";

setGlobalOptions({ maxInstances: 10 });

// Test endpoint
export const ping = onRequest((req, res) => {
  res.json({ message: "pong" });
});

// --------------------
// BGG Endpoints
// --------------------

// /bgg/search
export const searchGames = onRequest((request, response) => {
    const query = request.query.query || "";
    response.json({ data: "TODO", endpoint: "/bgg/search", query });
});

// /bgg/getByIds
export const getGamesByIds = onRequest((request, response) => {
  const ids = request.query.ids || "";
  response.json({ data: "TODO", endpoint: "/bgg/getByIds", ids });
});