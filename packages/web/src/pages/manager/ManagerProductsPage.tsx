import { useEffect, useState } from "react";
import { ImageIcon } from "lucide-react";
import { managerAPI, productAPI } from "@ebike/shared-code/api";
import type { Product } from "@ebike/shared-code/types";
import ProductImageManager from "../../components/manager/ProductImageManager";
import { attachImageFallback, createProductImageFallback, resolveProductImage } from "../../utils/media";

const formatCurrency = (value: number) =>
  new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 0 }).format(value);

const LOW_STOCK_THRESHOLD = 5;

const getStockBadge = (stockQuantity: number) => {
  if (stockQuantity <= 0) {
    return { label: "Hết hàng", className: "border-red-200 bg-red-50 text-red-700" };
  }
  if (stockQuantity <= LOW_STOCK_THRESHOLD) {
    return { label: "Sắp hết hàng", className: "border-amber-200 bg-amber-50 text-amber-700" };
  }
  return { label: "Đang bán", className: "border-green-200 bg-green-50 text-green-700" };
};

const ManagerProductsPage = () => {
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [imageManagerProduct, setImageManagerProduct] = useState<Product | null>(null);

  useEffect(() => {
    const loadProducts = async () => {
      try {
        setLoading(true);
        setProducts(await productAPI.list());
        setError("");
      } catch (productsError) {
        setError(productsError instanceof Error ? productsError.message : "Không thể tải danh sách sản phẩm.");
      } finally {
        setLoading(false);
      }
    };

    void loadProducts();
  }, []);

  if (loading) {
    return <div className="rounded-lg border border-slate-200 bg-white px-6 py-10 text-sm text-slate-500">Đang tải sản phẩm...</div>;
  }

  return (
    <div className="space-y-6">
      {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

      <section className="grid gap-5 xl:grid-cols-2">
        {products.map((product) => (
          <article key={product.id} className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
            <div className="grid gap-0 md:grid-cols-[240px,1fr]">
              <div className="h-[220px] bg-slate-100 md:h-full">
                <img
                  src={resolveProductImage(product.images?.[0]) || createProductImageFallback(product.name)}
                  alt={product.name}
                  className="h-full w-full object-cover"
                  onError={(event) => attachImageFallback(event, product.name)}
                />
              </div>
              <div className="p-6">
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">{product.category?.name || "Xe điện"}</p>
                    <h3 className="mt-2 text-xl font-bold text-slate-950">{product.name}</h3>
                  </div>
                  <span className="rounded-md bg-green-50 px-2.5 py-1 text-[10px] font-bold uppercase tracking-[0.16em] text-green-700">
                    Đang bán
                  </span>
                </div>

                <div className="mt-5 grid grid-cols-2 gap-4">
                  <div>
                    <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Giá bán</p>
                    <p className="mt-2 text-base font-bold text-slate-950">{formatCurrency(product.discountPrice ?? product.price)}</p>
                  </div>
                  <div>
                    <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Tồn kho</p>
                    <p className="mt-2 text-base font-bold text-slate-950">{product.stockQuantity ?? 0} chiếc</p>
                  </div>
                </div>

                <p className="mt-5 line-clamp-3 text-sm leading-7 text-slate-600">{product.description}</p>
                <button
                  type="button"
                  onClick={() => setImageManagerProduct(product)}
                  className="mt-5 inline-flex items-center gap-2 rounded-lg border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
                >
                  <ImageIcon className="h-4 w-4" />
                  Quản lý ảnh
                </button>
              </div>
            </div>
          </article>
        ))}
      </section>

      {imageManagerProduct && (
        <ProductImageManager
          productId={imageManagerProduct.id}
          productName={imageManagerProduct.name}
          onClose={() => setImageManagerProduct(null)}
        />
      )}
    </div>
  );
};

const ManagerProductsInventoryPage = () => {
  const [products, setProducts] = useState<Product[]>([]);
  const [stockInputs, setStockInputs] = useState<Record<number, string>>({});
  const [savingProductId, setSavingProductId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [stockMessage, setStockMessage] = useState("");

  useEffect(() => {
    const loadProducts = async () => {
      try {
        setLoading(true);
        const data = await productAPI.list();
        setProducts(data);
        setStockInputs(
          data.reduce<Record<number, string>>((result, product) => {
            result[product.id] = String(product.stockQuantity ?? 0);
            return result;
          }, {})
        );
        setError("");
      } catch (productsError) {
        setError(productsError instanceof Error ? productsError.message : "Không thể tải danh sách sản phẩm.");
      } finally {
        setLoading(false);
      }
    };

    void loadProducts();
  }, []);

  const updateStockInput = (productId: number, value: string) => {
    if (value === "" || /^\d+$/.test(value)) {
      setStockInputs((current) => ({ ...current, [productId]: value }));
    }
  };

  const saveStock = async (product: Product) => {
    const rawValue = stockInputs[product.id] ?? "0";
    const nextStockQuantity = Number(rawValue);

    if (rawValue === "" || !Number.isInteger(nextStockQuantity) || nextStockQuantity < 0) {
      setError("Tồn kho phải là số nguyên không âm.");
      return;
    }

    setSavingProductId(product.id);
    setError("");
    setStockMessage("");
    try {
      const updatedProduct = await managerAPI.updateProductStock(product.id, { stockQuantity: nextStockQuantity });
      setProducts((currentProducts) =>
        currentProducts.map((currentProduct) => (currentProduct.id === updatedProduct.id ? updatedProduct : currentProduct))
      );
      setStockInputs((current) => ({ ...current, [updatedProduct.id]: String(updatedProduct.stockQuantity ?? 0) }));
      setStockMessage(`Đã cập nhật tồn kho cho ${updatedProduct.name}.`);
    } catch (stockError) {
      setError(stockError instanceof Error ? stockError.message : "Không thể cập nhật tồn kho.");
    } finally {
      setSavingProductId(null);
    }
  };

  if (loading) {
    return <div className="rounded-lg border border-slate-200 bg-white px-6 py-10 text-sm text-slate-500">Đang tải sản phẩm...</div>;
  }

  return (
    <div className="space-y-6">
      {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
      {stockMessage && <div className="rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">{stockMessage}</div>}

      <section className="grid gap-5 xl:grid-cols-2">
        {products.map((product) => {
          const stockQuantity = product.stockQuantity ?? 0;
          const stockBadge = getStockBadge(stockQuantity);
          const stockInput = stockInputs[product.id] ?? String(stockQuantity);
          const stockChanged = stockInput !== String(stockQuantity);
          const inputInvalid = stockInput === "" || Number(stockInput) < 0;

          return (
            <article key={product.id} className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
              <div className="grid gap-0 md:grid-cols-[240px,1fr]">
                <div className="h-[220px] bg-slate-100 md:h-full">
                  <img
                    src={resolveProductImage(product.images?.[0]) || createProductImageFallback(product.name)}
                    alt={product.name}
                    className="h-full w-full object-cover"
                    onError={(event) => attachImageFallback(event, product.name)}
                  />
                </div>
                <div className="p-6">
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">{product.category?.name || "Xe điện"}</p>
                      <h3 className="mt-2 text-xl font-bold text-slate-950">{product.name}</h3>
                    </div>
                    <span className={`rounded-md border px-2.5 py-1 text-[10px] font-bold uppercase tracking-[0.16em] ${stockBadge.className}`}>
                      {stockBadge.label}
                    </span>
                  </div>

                  <div className="mt-5 grid grid-cols-2 gap-4">
                    <div>
                      <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Giá bán</p>
                      <p className="mt-2 text-base font-bold text-slate-950">{formatCurrency(product.discountPrice ?? product.price)}</p>
                    </div>
                    <div>
                      <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Tồn kho hiện tại</p>
                      <p className={`mt-2 text-base font-bold ${stockQuantity <= LOW_STOCK_THRESHOLD ? "text-amber-700" : "text-slate-950"}`}>
                        {stockQuantity} chiếc
                      </p>
                    </div>
                  </div>

                  <div className="mt-5 rounded-lg border border-slate-200 bg-slate-50 p-4">
                    <label className="block">
                      <span className="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Cập nhật tồn kho</span>
                      <div className="mt-2 flex flex-col gap-3 sm:flex-row">
                        <input
                          type="number"
                          min={0}
                          step={1}
                          value={stockInput}
                          onChange={(event) => updateStockInput(product.id, event.target.value)}
                          className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-900 outline-none transition focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
                        />
                        <button
                          type="button"
                          onClick={() => void saveStock(product)}
                          disabled={!stockChanged || inputInvalid || savingProductId === product.id}
                          className="rounded-lg bg-slate-950 px-4 py-2 text-sm font-bold text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          {savingProductId === product.id ? "Đang lưu..." : "Lưu"}
                        </button>
                      </div>
                    </label>
                    {stockQuantity <= 0 ? (
                      <p className="mt-3 text-xs font-semibold text-red-700">Sản phẩm đã hết hàng, cần bổ sung trước khi bán tiếp.</p>
                    ) : stockQuantity <= LOW_STOCK_THRESHOLD ? (
                      <p className="mt-3 text-xs font-semibold text-amber-700">Sản phẩm sắp hết hàng, nên nhập thêm tồn kho.</p>
                    ) : null}
                  </div>

                  <p className="mt-5 line-clamp-3 text-sm leading-7 text-slate-600">{product.description}</p>
                </div>
              </div>
            </article>
          );
        })}
      </section>
    </div>
  );
};

export default ManagerProductsPage;
