// Cache.ts

import * as admin from "firebase-admin";

/**
 * Generic two-level cache (L1 in-memory + L2 Firestore)
 *
 * Usage:
 *   const cache = new Cache<GameOut>(ttlMs, maxSize, firestoreCollection);
 *   await cache.get(key);
 *   await cache.set(key, value);
 */
export class Cache<T> {
  private l1Cache: Map<string, CacheEntry<T>>;
  private readonly ttlMs: number;
  private readonly maxSize: number;
  private readonly collection: string;
  private readonly db: FirebaseFirestore.Firestore;

  constructor(ttlMs: number, maxSize: number, firestoreCollection: string) {
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
  async get(key: string): Promise<T | null> {
    // Check L1
    const l1Result = this.getFromL1(key);
    if (l1Result !== null) return l1Result;

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
  async set(key: string, value: T): Promise<void> {
    this.setToL1(key, value);
    await this.setToL2(key, value);
  }

  /**
   * Clear all L1 cache (useful for tests)
   */
  clearL1(): void {
    this.l1Cache.clear();
  }

  // ---------- L1 (in-memory) methods ----------

  private getFromL1(key: string): T | null {
    const entry = this.l1Cache.get(key);
    if (!entry) return null;

    if (Date.now() > entry.expireAt) {
      this.l1Cache.delete(key);
      return null;
    }

    return entry.value;
  }

  private setToL1(key: string, value: T): void {
    this.l1Cache.set(key, {
      value,
      expireAt: Date.now() + this.ttlMs,
    });

    // LRU eviction
    while (this.l1Cache.size > this.maxSize) {
      const firstKey = this.l1Cache.keys().next().value;
      if (!firstKey) break;
      this.l1Cache.delete(firstKey);
    }
  }

  // ---------- L2 (Firestore) methods ----------

  private async getFromL2(key: string): Promise<T | null> {
    try {
      const docRef = this.db.collection(this.collection).doc(key);
      const doc = await docRef.get();

      if (!doc.exists) return null;

      const data = doc.data();
      if (!data || data.value == null || data.updatedAt == null) return null;

      // Handle different timestamp formats
      const updatedAtMs = this.extractTimestamp(data.updatedAt);

      if (Date.now() - updatedAtMs > this.ttlMs) {
        // Expired - delete and return null
        await docRef.delete().catch(() => {
          // Ignore deletion errors
        });
        return null;
      }

      return data.value as T;
    } catch (err) {
      // Log warning but don't crash
      console.warn(`[Cache] getFromL2 failed for key ${key}:`, err);
      return null;
    }
  }

  private async setToL2(key: string, value: T): Promise<void> {
    try {
      const docRef = this.db.collection(this.collection).doc(key);
      await docRef.set({
        value,
        updatedAt: Date.now(),
      });
    } catch (err) {
      // Log warning but don't crash
      console.warn(`[Cache] setToL2 failed for key ${key}:`, err);
    }
  }

  private extractTimestamp(updatedAt: any): number {
    if (typeof updatedAt === "number") return updatedAt;
    if (updatedAt && typeof updatedAt.toMillis === "function") return updatedAt.toMillis();
    if (updatedAt && typeof updatedAt.toDate === "function") return updatedAt.toDate().getTime();
    return Date.now();
  }
}

type CacheEntry<T> = {
  value: T;
  expireAt: number;
};