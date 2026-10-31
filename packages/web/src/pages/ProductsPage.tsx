import { useEffect, useMemo, useState } from "react";
import { ChevronRight, Heart, Loader2, Search } from "lucide-react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { productAPI } from "@ebike/shared-code/api";
import { useAuth, useFavorites } from "@ebike/shared-code/hooks";
import type { Product, ProductFilter, ProductFilterOptions } from "@ebike/shared-code/types";
import { attachImageFallback, resolveProductImage } from "../utils/media";

type SortKey = "default" | "price-asc" | "price-desc" | "name-asc" | "newest";

const PAGE_SIZE = 9;

const PRICE_OPTIONS = [
  { label: "Tất cả", value: "all", minPrice: undefined, maxPrice: undefined },
  { label: "Dưới 20 triệu", value: "under20", minPrice: undefined, maxPrice: 20_000_000 },
  { label: "20 - 50 triệu", value: "20-50", minPrice: 20_000_000, maxPrice: 50_000_000 },
  { label: "Trên 50 triệu", value: "over50", minPrice: 50_000_000, maxPrice: undefined }
] as const;

const RANGE_OPTIONS = [
  { label: "Tất cả", value: "all", minRangeKm: undefined, maxRangeKm: undefined },
  { label: "Dưới 50 km", value: "under50", minRangeKm: undefined, maxRangeKm: 50 },
  { label: "50 - 100 km", value: "50-100", minRangeKm: 50, maxRangeKm: 100 },
  { label: "Trên 100 km", value: "over100", minRangeKm: 100, maxRangeKm: undefined }
] as const;

const BATTERY_TYPE_LABELS: Record<string, string> = {
  LEAD_ACID: "Axit-chì",
  LITHIUM_ION: "Lithium-ion",
  LFP: "LFP"
};

const parseSortParams = (sortKey: SortKey): Pick<ProductFilter, "sortBy" | "sortDir"> => {
  switch (sortKey) {
    case "price-asc":
      return { sortBy: "price", sortDir: "asc" };
    case "price-desc":
      return { sortBy: "price", sortDir: "desc" };
    case "name-asc":
      return { sortBy: "name", sortDir: "asc" };
    case "newest":
      return { sortBy: "newest", sortDir: "desc" };
    default:
      return {};
  }
};

const ProductsPage = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { isAuthenticated } = useAuth();
  const { favoriteIdSet, isPending, toggleFavorite, error: favoriteError } = useFavorites();
  const [products, setProducts] = useState<Product[]>([]);
  const [allCategories, setAllCategories] = useState<Array<{ id: number; name: string }>>([]);
  const [filterOptions, setFilterOptions] = useState<ProductFilterOptions>({ brands: [], vehicleTypes: [], batteryTypes: [] });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchText, setSearchText] = useState(searchParams.get("q") || "");
  const [debouncedQuery, setDebouncedQuery] = useState(searchParams.get("q") || "");
  const [currentPage, setCurrentPage] = useState(Number(searchParams.get("page") || "1"));

  const categoryParam = searchParams.get("category");
  const selectedCategoryId =
    categoryParam && !Number.isNaN(Number(categoryParam)) ? Number(categoryParam) : null;
  const selectedPriceKey = (searchParams.get("price") as (typeof PRICE_OPTIONS)[number]["value"]) || "all";
  const selectedRangeKey = (searchParams.get("range") as (typeof RANGE_OPTIONS)[number]["value"]) || "all";
  const selectedBrand = searchParams.get("brand") || "";
  const selectedBatteryType = searchParams.get("batteryType") || "";
  const inStockOnly = searchParams.get("inStock") === "1";
  const sortBy = (searchParams.get("sort") as SortKey) || "default";

  const selectedCompareSlugs = useMemo(() => {
    const compareParam = searchParams.get("compare");
    if (!compareParam) {
      return [];
    }

    return compareParam
      .split(",")
      .map((value) => value.trim())
      .filter(Boolean)
      .slice(0, 3);
  }, [searchParams]);

  const updateCompareParams = (compareSlugs: string[]) => {
    const nextParams = new URLSearchParams(searchParams);
    if (compareSlugs.length > 0) {
      nextParams.set("compare", compareSlugs.join(","));
    } else {
      nextParams.delete("compare");
    }
    setSearchParams(nextParams, { replace: true });
  };

  const toggleCompare = (productSlug: string) => {
    const nextCompareSlugs = selectedCompareSlugs.includes(productSlug)
      ? selectedCompareSlugs.filter((slug) => slug !== productSlug)
      : [...selectedCompareSlugs, productSlug].slice(0, 3);

    updateCompareParams(nextCompareSlugs);
  };

  const updateSearchParams = (updates: Record<string, string | null>) => {
    const nextParams = new URLSearchParams(searchParams);
    Object.entries(updates).forEach(([key, value]) => {
      if (!value) {
        nextParams.delete(key);
      } else {
        nextParams.set(key, value);
      }
    });
    nextParams.delete("page");
    setSearchParams(nextParams, { replace: true });
    setCurrentPage(1);
  };

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedQuery(searchText.trim()), 350);
    return () => window.clearTimeout(timer);
  }, [searchText]);

  useEffect(() => {
    const nextQuery = debouncedQuery || "";
    const currentQuery = searchParams.get("q") || "";
    if (nextQuery !== currentQuery) {
      const nextParams = new URLSearchParams(searchParams);
      if (nextQuery) {
        nextParams.set("q", nextQuery);
      } else {
        nextParams.delete("q");
      }
      nextParams.delete("page");
      setSearchParams(nextParams, { replace: true });
    }
  }, [debouncedQuery, searchParams, setSearchParams]);

  useEffect(() => {
    const loadMeta = async () => {
      try {
        const [items, options] = await Promise.all([productAPI.list(), productAPI.getFilterOptions()]);
        const uniqueCategories = Array.from(
          new Map(
            items
              .filter((item) => item.category?.id && item.category?.name)
              .map((item) => [item.category.id, { id: item.category.id, name: item.category.name }])
          ).values()
        );
        setAllCategories(uniqueCategories);
        setFilterOptions(options);
      } catch {
      }
    };

    void loadMeta();
  }, []);

  useEffect(() => {
    const selectedPrice = PRICE_OPTIONS.find((option) => option.value === selectedPriceKey);
    const selectedRange = RANGE_OPTIONS.find((option) => option.value === selectedRangeKey);
    const filters: ProductFilter = {
      query: debouncedQuery || undefined,
      categoryId: selectedCategoryId ?? undefined,
      minPrice: selectedPrice?.minPrice,
      maxPrice: selectedPrice?.maxPrice,
      brand: selectedBrand || undefined,
      batteryType: selectedBatteryType || undefined,
      minRangeKm: selectedRange?.minRangeKm,
      maxRangeKm: selectedRange?.maxRangeKm,
      inStock: inStockOnly ? true : undefined,
      ...parseSortParams(sortBy)
    };

    const loadProducts = async () => {
      setLoading(true);
      try {
        const data = await productAPI.list(filters);
        setProducts(data);
        setError(null);
      } catch (fetchError) {
        setError(fetchError instanceof Error ? fetchError.message : "Không thể tải sản phẩm");
        setProducts([]);
      } finally {
        setLoading(false);
      }
    };

    void loadProducts();
  }, [
    debouncedQuery,
    selectedCategoryId,
    selectedPriceKey,
    selectedRangeKey,
    selectedBrand,
    selectedBatteryType,
    inStockOnly,
    sortBy
  ]);

  useEffect(() => {
    setCurrentPage(1);
  }, [
    debouncedQuery,
    selectedCategoryId,
    selectedPriceKey,
    selectedRangeKey,
    selectedBrand,
    selectedBatteryType,
    inStockOnly,
    sortBy
  ]);

  const sortedProducts = products;

  const totalPages = Math.max(1, Math.ceil(sortedProducts.length / PAGE_SIZE));

  useEffect(() => {
    setCurrentPage((page) => Math.min(page, totalPages));
  }, [totalPages]);

  const paginatedProducts = useMemo(() => {
    const startIndex = (currentPage - 1) * PAGE_SIZE;
    return sortedProducts.slice(startIndex, startIndex + PAGE_SIZE);
  }, [currentPage, sortedProducts]);

  const displayProducts = useMemo(
    () =>
      paginatedProducts.map((item, index) => ({
        id: item.slug || String(item.id),
        productId: item.id,
        name: item.name,
        slug: item.slug,
        type: item.category?.name || "E-BIKE",
        price: `${(item.discountPrice ?? item.price).toLocaleString("vi-VN")}đ`,
        priceValue: item.discountPrice ?? item.price,
        image:
          item.images?.[0] ||
          "https://images.unsplash.com/photo-1558981806-ec527fa84c39?auto=format&fit=crop&q=80&w=600",
        badge: item.featured ? "FEATURED" : index === 0 ? "BEST SELLER" : "",
        colors: ["#1a1a1a", "#3b82f6", "#d1d5db"],
        meta: item.reviewCount ? `${item.reviewCount} đánh giá` : item.stockQuantity ? `${item.stockQuantity} trong kho` : "Sản phẩm mới"
      })),
    [paginatedProducts]
  );

  const handleFavoriteClick = async (productId: number) => {
    if (!isAuthenticated) {
      navigate("/auth");
      return;
    }

    await toggleFavorite(productId);
  };

  return (
    <div className="pt-20">
      <section className="relative h-[60vh] overflow-hidden">
        <img
          src="https://images.unsplash.com/photo-1615172282427-9a57ef2d142e?auto=format&fit=crop&q=80&w=2000"
          alt="Collection"
          className="absolute inset-0 h-full w-full object-cover"
          referrerPolicy="no-referrer"
        />
        <div className="absolute inset-0 flex items-center bg-gradient-to-r from-black/60 to-transparent px-6 md:px-12">
          <div className="mx-auto w-full max-w-7xl">
            <span className="mb-4 block text-sm font-bold tracking-widest text-primary">2026 COLLECTION</span>
            <h1 className="mb-6 text-6xl font-bold leading-none tracking-tighter text-white md:text-8xl">
              Precision
              <br />
              <span className="text-primary">Engineering.</span>
            </h1>
            <p className="max-w-md text-lg leading-relaxed text-white/70">
              Discover the next generation of electric mobility. Engineered for performance, designed for the future.
            </p>
          </div>
        </div>
      </section>

      <div className="mx-auto max-w-7xl px-6 py-16 md:px-12">
        <div className="flex flex-col gap-12 lg:flex-row">
          <aside className="w-full flex-shrink-0 space-y-10 lg:w-72">
            <div>
              <h3 className="mb-6 text-sm font-bold uppercase tracking-wider">Tìm kiếm</h3>
              <div className="relative">
                <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <input
                  value={searchText}
                  onChange={(event) => setSearchText(event.target.value)}
                  placeholder="Tên xe, mô tả..."
                  className="input-base pl-10"
                />
              </div>
            </div>

            <div>
              <h3 className="mb-6 text-sm font-bold uppercase tracking-wider">Dòng xe</h3>
              <div className="space-y-3">
                <label className="flex cursor-pointer items-center gap-3">
                  <input
                    type="radio"
                    checked={selectedCategoryId === null}
                    onChange={() => updateSearchParams({ category: null })}
                    className="h-5 w-5 border-outline-variant text-primary focus:ring-primary"
                  />
                  <span className="text-muted-foreground">Tất cả</span>
                </label>
                {allCategories.map((category) => (
                  <label key={category.id} className="flex cursor-pointer items-center gap-3">
                    <input
                      type="radio"
                      checked={selectedCategoryId === category.id}
                      onChange={() => updateSearchParams({ category: String(category.id) })}
                      className="h-5 w-5 border-outline-variant text-primary focus:ring-primary"
                    />
                    <span className="text-muted-foreground">{category.name}</span>
                  </label>
                ))}
              </div>
            </div>

            <div>
              <h3 className="mb-6 text-sm font-bold uppercase tracking-wider">Phân khúc giá</h3>
              <div className="space-y-3">
                {PRICE_OPTIONS.map((option) => (
                  <label key={option.value} className="flex cursor-pointer items-center gap-3">
                    <input
                      type="radio"
                      name="price-range"
                      checked={selectedPriceKey === option.value}
                      onChange={() => updateSearchParams({ price: option.value === "all" ? null : option.value })}
                      className="h-5 w-5 border-outline-variant text-primary focus:ring-primary"
                    />
                    <span className="text-muted-foreground">{option.label}</span>
                  </label>
                ))}
              </div>
            </div>

            <div>
              <h3 className="mb-6 text-sm font-bold uppercase tracking-wider">Hãng</h3>
              <select
                value={selectedBrand}
                onChange={(event) => updateSearchParams({ brand: event.target.value || null })}
                className="input-base w-full"
              >
                <option value="">Tất cả hãng</option>
                {filterOptions.brands.map((brand) => (
                  <option key={brand} value={brand}>
                    {brand}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <h3 className="mb-6 text-sm font-bold uppercase tracking-wider">Loại pin</h3>
              <select
                value={selectedBatteryType}
                onChange={(event) => updateSearchParams({ batteryType: event.target.value || null })}
                className="input-base w-full"
              >
                <option value="">Tất cả loại pin</option>
                {filterOptions.batteryTypes.map((batteryType) => (
                  <option key={batteryType} value={batteryType}>
                    {BATTERY_TYPE_LABELS[batteryType] || batteryType}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <h3 className="mb-6 text-sm font-bold uppercase tracking-wider">Quãng đường</h3>
              <div className="space-y-3">
                {RANGE_OPTIONS.map((option) => (
                  <label key={option.value} className="flex cursor-pointer items-center gap-3">
                    <input
                      type="radio"
                      name="range-filter"
                      checked={selectedRangeKey === option.value}
                      onChange={() => updateSearchParams({ range: option.value === "all" ? null : option.value })}
                      className="h-5 w-5 border-outline-variant text-primary focus:ring-primary"
                    />
                    <span className="text-muted-foreground">{option.label}</span>
                  </label>
                ))}
              </div>
            </div>

            <div>
              <h3 className="mb-6 text-sm font-bold uppercase tracking-wider">Tồn kho</h3>
              <label className="flex cursor-pointer items-center gap-3">
                <input
                  type="checkbox"
                  checked={inStockOnly}
                  onChange={(event) => updateSearchParams({ inStock: event.target.checked ? "1" : null })}
                  className="h-5 w-5 rounded border-outline-variant text-primary focus:ring-primary"
                />
                <span className="text-muted-foreground">Chỉ hiển thị còn hàng</span>
              </label>
            </div>
          </aside>

          <main className="flex-grow">
            <div className="mb-10 flex flex-col items-start justify-between gap-4 sm:flex-row sm:items-center">
              <div>
                <h2 className="text-3xl font-bold">Tất cả sản phẩm</h2>
                <p className="mt-1 text-sm text-muted-foreground">
                  Hiển thị {displayProducts.length} / {sortedProducts.length} mẫu xe
                </p>
              </div>
              <div className="flex items-center gap-3">
                <select
                  value={sortBy}
                  onChange={(event) => updateSearchParams({ sort: event.target.value === "default" ? null : event.target.value })}
                  className="rounded-lg border border-outline-variant bg-white px-4 py-2 text-sm font-bold focus:border-primary focus:outline-none"
                >
                  <option value="default">Mặc định</option>
                  <option value="price-asc">Giá tăng dần</option>
                  <option value="price-desc">Giá giảm dần</option>
                  <option value="name-asc">Tên A-Z</option>
                  <option value="newest">Mới nhất</option>
                </select>
              </div>
            </div>

            {selectedCompareSlugs.length > 0 ? (
              <div className="mb-6 rounded-3xl border border-primary/15 bg-primary/5 p-4 text-sm text-stone-700 sm:flex sm:items-center sm:justify-between">
                <div>
                  <p className="font-bold">Đã chọn {selectedCompareSlugs.length} sản phẩm để so sánh</p>
                  <p className="text-sm text-muted-foreground">Chọn tối đa 3 sản phẩm. Nhấn nút So sánh để xem chi tiết đối chiếu.</p>
                </div>
                <div className="mt-3 flex flex-wrap items-center gap-2 sm:mt-0">
                  <Link
                    to={`/compare?ids=${selectedCompareSlugs.join(",")}`}
                    className={`rounded-full bg-primary px-4 py-2 text-sm font-bold text-white transition ${
                      selectedCompareSlugs.length < 2 ? "opacity-50 pointer-events-none" : "hover:bg-primary/90"
                    }`}
                  >
                    So sánh
                  </Link>
                  <button
                    type="button"
                    onClick={() => updateCompareParams([])}
                    className="rounded-full border border-stone-200 bg-white px-4 py-2 text-sm font-bold text-stone-700 transition hover:bg-stone-100"
                  >
                    Xóa chọn
                  </button>
                </div>
              </div>
            ) : null}

            {loading ? (
              <div className="py-16 text-center text-muted-foreground">Đang tải sản phẩm...</div>
            ) : error ? (
              <div className="py-16 text-center text-red-500">{error}</div>
            ) : displayProducts.length === 0 ? (
              <div className="py-16 text-center text-muted-foreground">Không có sản phẩm phù hợp với bộ lọc API hiện tại.</div>
            ) : (
              <>
                {favoriteError ? (
                  <div className="mb-6 rounded-2xl border border-outline-variant/15 bg-surface-container-low px-4 py-3 text-sm text-muted-foreground">
                    {favoriteError}
                  </div>
                ) : null}

              <div className="grid grid-cols-1 gap-8 md:grid-cols-2 xl:grid-cols-3">
                {displayProducts.map((product) => (
                  <div
                    key={product.id}
                    className="group overflow-hidden rounded-2xl border border-outline-variant/10 bg-white transition-all duration-500 hover:shadow-xl"
                  >
                    <div className="relative aspect-[4/3] overflow-hidden bg-surface-container-low">
                      <img
                        src={resolveProductImage(product.image)}
                        alt={product.name}
                        className="h-full w-full object-cover transition-transform duration-700 group-hover:scale-110"
                        onError={(event) => attachImageFallback(event, product.name)}
                        referrerPolicy="no-referrer"
                      />
                      <button
                        type="button"
                        onClick={() => void handleFavoriteClick(product.productId)}
                        disabled={isPending(product.productId)}
                        className={`absolute right-4 top-4 flex h-11 w-11 items-center justify-center rounded-full border transition-all ${
                          favoriteIdSet.has(product.productId)
                            ? "border-primary bg-primary text-white shadow-lg shadow-primary/25"
                            : "border-white/80 bg-white/90 text-foreground hover:border-primary hover:text-primary"
                        } disabled:cursor-not-allowed disabled:opacity-70`}
                        aria-label={
                          favoriteIdSet.has(product.productId)
                            ? `Bỏ yêu thích ${product.name}`
                            : `Thêm ${product.name} vào yêu thích`
                        }
                        title={
                          isAuthenticated
                            ? favoriteIdSet.has(product.productId)
                              ? "Bỏ yêu thích"
                              : "Thêm vào yêu thích"
                            : "Đăng nhập để dùng danh sách yêu thích"
                        }
                      >
                        {isPending(product.productId) ? (
                          <Loader2 size={18} className="animate-spin" />
                        ) : (
                          <Heart size={18} fill={favoriteIdSet.has(product.productId) ? "currentColor" : "none"} />
                        )}
                      </button>
                      {product.badge ? (
                        <span className="absolute left-4 top-4 rounded-full bg-black px-3 py-1 text-[10px] font-bold tracking-widest text-white">
                          {product.badge}
                        </span>
                      ) : null}
                    </div>

                    <div className="p-6">
                      <div className="mb-2 flex items-start justify-between gap-4">
                        <h3 className="text-xl font-bold transition-colors group-hover:text-primary">{product.name}</h3>
                        <span className="font-bold text-primary">{product.price}</span>
                      </div>
                      <p className="mb-6 text-xs uppercase tracking-wider text-muted-foreground">
                        {product.type} | {product.meta}
                      </p>

                      <div className="mb-4 flex items-center justify-between rounded-2xl border border-stone-200 bg-stone-50 px-3 py-2 text-sm text-stone-600">
                        <label className="inline-flex items-center gap-2 cursor-pointer">
                          <input
                            type="checkbox"
                            checked={selectedCompareSlugs.includes(product.slug)}
                            onChange={() => toggleCompare(product.slug)}
                            className="h-4 w-4 rounded border-outline-variant text-primary focus:ring-primary"
                          />
                          <span>So sánh</span>
                        </label>
                        <span className="text-xs text-muted-foreground">
                          {selectedCompareSlugs.includes(product.slug) ? "Đã chọn" : "Chọn để so sánh"}
                        </span>
                      </div>

                      <div className="flex items-center justify-between">
                        <div className="flex gap-2">
                          {product.colors.map((color, index) => (
                            <div
                              key={`${product.id}-${index}`}
                              className="h-3 w-3 rounded-full border border-outline-variant/20"
                              style={{ backgroundColor: color }}
                            />
                          ))}
                        </div>
                        <Link to={`/product/${product.id}`} className="flex items-center gap-1 text-sm font-bold">
                          XEM CHI TIẾT
                          <div className="ml-2 h-[2px] w-8 bg-primary transition-all group-hover:w-12" />
                        </Link>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
              </>
            )}

            {totalPages > 1 ? (
              <div className="mt-16 flex items-center justify-center gap-2">
                {Array.from({ length: totalPages }, (_, index) => index + 1).map((page) => (
                  <button
                    key={page}
                    type="button"
                    onClick={() => setCurrentPage(page)}
                    className={`h-10 w-10 rounded-lg font-bold transition-colors ${
                      currentPage === page ? "bg-primary text-white" : "hover:bg-surface-container-low"
                    }`}
                    aria-label={`Go to page ${page}`}
                    aria-current={currentPage === page ? "page" : undefined}
                  >
                    {page}
                  </button>
                ))}
                <button
                  type="button"
                  onClick={() => setCurrentPage((page) => Math.min(page + 1, totalPages))}
                  disabled={currentPage === totalPages}
                  className="flex h-10 w-10 items-center justify-center rounded-lg transition-colors hover:bg-surface-container-low disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent"
                  aria-label="Go to next page"
                >
                  <ChevronRight size={20} />
                </button>
              </div>
            ) : null}
          </main>
        </div>
      </div>
    </div>
  );
};

export default ProductsPage;
