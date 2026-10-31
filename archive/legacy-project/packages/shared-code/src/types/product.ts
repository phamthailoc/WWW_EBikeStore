export interface Category {
  id: number;
  name: string;
  slug?: string;
}

export interface Review {
  id: number;
  productId?: number;
  productSlug?: string;
  userId?: number;
  username?: string;
  rating: number;
  title?: string | null;
  comment?: string | null;
  authorName: string;
  createdAt?: string;
  editableByCurrentUser?: boolean;
}

export interface ReviewRequest {
  rating: number;
  title?: string;
  comment?: string;
}

export interface Product {
  id: number;
  name: string;
  slug: string;
  description: string;
  price: number;
  discountPrice?: number | null;
  rating?: number | null;
  reviewCount?: number | null;
  stockQuantity?: number | null;
  featured?: boolean | null;
  category: Category;
  images: string[];
  reviews?: Review[];
}

export interface ProductSpecification {
  modelCode?: string | null;
  brand?: string | null;
  vehicleType?: string | null;
  batteryType?: string | null;
  batteryCapacityAh?: number | null;
  maxSpeedKmh?: number | null;
  maxRangeKm?: number | null;
  motorPowerWatts?: number | null;
  brakeType?: string | null;
  driveType?: string | null;
  chargingTimeHours?: number | null;
  productWeightKg?: number | null;
  warrantyMonths?: number | null;
  smartFeatures?: string | null;
}

export interface ProductVariant {
  id?: number | null;
  sku?: string | null;
  variantName?: string | null;
  colorName?: string | null;
  colorHex?: string | null;
  imageUrl?: string | null;
  batteryCapacityAh?: number | null;
  additionalPrice?: number | null;
  stockQuantity?: number | null;
  defaultVariant?: boolean | null;
}

export interface ProductDetail {
  id: number;
  name: string;
  slug: string;
  description: string;
  price: number;
  discountPrice?: number | null;
  rating?: number | null;
  reviewCount?: number | null;
  stockQuantity?: number | null;
  featured?: boolean | null;
  category?: { id?: number; name?: string; slug?: string } | null;
  specification?: ProductSpecification | null;
  variants?: ProductVariant[] | null;
  images?: string[] | null;
}

export interface ProductFilter {
  query?: string;
  categoryId?: number;
  minPrice?: number;
  maxPrice?: number;
  brand?: string;
  vehicleType?: string;
  batteryType?: string;
  minRangeKm?: number;
  maxRangeKm?: number;
  inStock?: boolean;
  sortBy?: "price" | "name" | "newest" | string;
  sortDir?: "asc" | "desc" | string;
}

export interface ProductFilterOptions {
  brands: string[];
  vehicleTypes: string[];
  batteryTypes: string[];
}

export interface AdminProductImage {
  id: number;
  productId: number;
  variantId?: number | null;
  imageUrl: string;
  altText?: string | null;
  sortOrder?: number | null;
  primaryImage?: boolean | null;
  status?: string | null;
  createdAt?: string | null;
}
