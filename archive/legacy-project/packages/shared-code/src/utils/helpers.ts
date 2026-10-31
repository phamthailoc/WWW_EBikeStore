export const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

export const groupBy = <T>(items: T[], keySelector: (item: T) => string) =>
  items.reduce<Record<string, T[]>>((groups, item) => {
    const key = keySelector(item);
    groups[key] = groups[key] ?? [];
    groups[key].push(item);
    return groups;
  }, {});
