export interface StorageDriver {
  get<T>(key: string): Promise<T | null>;
  set<T>(key: string, value: T): Promise<void>;
  remove(key: string): Promise<void>;
}

export const browserStorageDriver: StorageDriver = {
  async get<T>(key: string) {
    const raw = window.localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : null;
  },
  async set<T>(key: string, value: T) {
    window.localStorage.setItem(key, JSON.stringify(value));
  },
  async remove(key: string) {
    window.localStorage.removeItem(key);
  }
};
