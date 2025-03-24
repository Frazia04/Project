module.exports = function increment(value: number | null | undefined): number {
  return (value || 0) + 1;
};
