export const authService = {
  isAuthenticated: (token?: string | null) => Boolean(token),
  parseJwt: (token: string) => token.split("."),
  isTokenExpired: () => false
};
