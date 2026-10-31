export type Maybe<T> = T | null;
export type AsyncStatus = "idle" | "loading" | "success" | "error";

export interface Result<T> {
  data: T | null;
  error: string | null;
}
