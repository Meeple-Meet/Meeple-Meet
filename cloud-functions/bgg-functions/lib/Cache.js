"use strict";
// Cache.ts
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
Object.defineProperty(exports, "__esModule", { value: true });
exports.Cache = void 0;
const admin = __importStar(require("firebase-admin"));
/**
 * Generic two-level cache (L1 in-memory + L2 Firestore)
 *
 * Usage:
 *   const cache = new Cache<GameOut>(ttlMs, maxSize, firestoreCollection);
 *   await cache.get(key);
 *   await cache.set(key, value);
 */
class Cache {
    constructor(ttlMs, maxSize, firestoreCollection) {
        this.ttlMs = ttlMs;
        this.maxSize = maxSize;
        this.collection = firestoreCollection;
        this.l1Cache = new Map();
        this.db = admin.firestore();
    }
    /**
     * Get value from cache (checks L1, then L2)
     * Returns null if not found or expired
     */
    async get(key) {
        // Check L1
        const l1Result = this.getFromL1(key);
        if (l1Result !== null)
            return l1Result;
        // Check L2
        const l2Result = await this.getFromL2(key);
        if (l2Result !== null) {
            // Populate L1 for next time
            this.setToL1(key, l2Result);
            return l2Result;
        }
        return null;
    }
    /**
     * Set value in both L1 and L2 caches
     */
    async set(key, value) {
        this.setToL1(key, value);
        await this.setToL2(key, value);
    }
    /**
     * Clear all L1 cache (useful for tests)
     */
    clearL1() {
        this.l1Cache.clear();
    }
    // ---------- L1 (in-memory) methods ----------
    getFromL1(key) {
        const entry = this.l1Cache.get(key);
        if (!entry)
            return null;
        if (Date.now() > entry.expireAt) {
            this.l1Cache.delete(key);
            return null;
        }
        return entry.value;
    }
    setToL1(key, value) {
        this.l1Cache.set(key, {
            value,
            expireAt: Date.now() + this.ttlMs,
        });
        // LRU eviction
        while (this.l1Cache.size > this.maxSize) {
            const firstKey = this.l1Cache.keys().next().value;
            if (!firstKey)
                break;
            this.l1Cache.delete(firstKey);
        }
    }
    // ---------- L2 (Firestore) methods ----------
    async getFromL2(key) {
        try {
            const docRef = this.db.collection(this.collection).doc(key);
            const doc = await docRef.get();
            if (!doc.exists)
                return null;
            const data = doc.data();
            if (!data || data.value == null || data.updatedAt == null)
                return null;
            // Handle different timestamp formats
            const updatedAtMs = this.extractTimestamp(data.updatedAt);
            if (Date.now() - updatedAtMs > this.ttlMs) {
                // Expired - delete and return null
                await docRef.delete().catch(() => {
                    // Ignore deletion errors
                });
                return null;
            }
            return data.value;
        }
        catch (err) {
            // Log warning but don't crash
            console.warn(`[Cache] getFromL2 failed for key ${key}:`, err);
            return null;
        }
    }
    async setToL2(key, value) {
        try {
            const docRef = this.db.collection(this.collection).doc(key);
            await docRef.set({
                value,
                updatedAt: Date.now(),
            });
        }
        catch (err) {
            // Log warning but don't crash
            console.warn(`[Cache] setToL2 failed for key ${key}:`, err);
        }
    }
    extractTimestamp(updatedAt) {
        if (typeof updatedAt === "number")
            return updatedAt;
        if (updatedAt && typeof updatedAt.toMillis === "function")
            return updatedAt.toMillis();
        if (updatedAt && typeof updatedAt.toDate === "function")
            return updatedAt.toDate().getTime();
        return Date.now();
    }
}
exports.Cache = Cache;
//# sourceMappingURL=Cache.js.map