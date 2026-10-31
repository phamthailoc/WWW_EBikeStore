import type { Product } from "../types";

export const productService = {
  searchProducts: (products: Product[], query: string) =>
    products.filter((product) => product.name.toLowerCase().includes(query.toLowerCase()))
};
