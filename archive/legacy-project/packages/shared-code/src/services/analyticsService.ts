export const analyticsService = {
  trackEvent: (name: string, payload?: Record<string, unknown>) =>
    console.info("[analytics:event]", name, payload),
  trackPageView: (path: string) => console.info("[analytics:page]", path)
};
