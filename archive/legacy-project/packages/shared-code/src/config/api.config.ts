declare const __API_BASE_URL__: string | undefined;

const DEFAULT_API_BASE_URL = "https://kineticstore.online/api/v1";

export const API_BASE_URL =
  typeof __API_BASE_URL__ !== "undefined" && __API_BASE_URL__
    ? __API_BASE_URL__
    : DEFAULT_API_BASE_URL;
