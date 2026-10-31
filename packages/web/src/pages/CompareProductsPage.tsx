import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { productAPI } from "@ebike/shared-code/api";
import type { ProductDetail } from "@ebike/shared-code/types";
import { attachImageFallback, resolveProductImage } from "../utils/media";

const compareRows = [
  {
    label: "Giá bán",
    value: (product: ProductDetail) => {
      const price = product.discountPrice ?? product.price;
      return `${price.toLocaleString("vi-VN")}đ`;
    }
  },
  {
    label: "Loại xe",
    value: (product: ProductDetail) => product.specification?.vehicleType || product.category?.name || "-"
  },
  {
    label: "Thương hiệu",
    value: (product: ProductDetail) => product.specification?.brand || "-"
  },
  {
    label: "Loại pin",
    value: (product: ProductDetail) => product.specification?.batteryType || "-"
  },
  {
    label: "Quãng đường tối đa",
    value: (product: ProductDetail) =>
      product.specification?.maxRangeKm ? `${product.specification.maxRangeKm} km` : "-"
  },
  {
    label: "Vận tốc tối đa",
    value: (product: ProductDetail) =>
      product.specification?.maxSpeedKmh ? `${product.specification.maxSpeedKmh} km/h` : "-"
  },
  {
    label: "Công suất động cơ",
    value: (product: ProductDetail) =>
      product.specification?.motorPowerWatts ? `${product.specification.motorPowerWatts} W` : "-"
  },
  {
    label: "Bảo hành",
    value: (product: ProductDetail) =>
      product.specification?.warrantyMonths ? `${product.specification.warrantyMonths} tháng` : "-"
  },
  {
    label: "Tồn kho",
    value: (product: ProductDetail) =>
      product.stockQuantity !== undefined && product.stockQuantity !== null
        ? `${product.stockQuantity} sản phẩm`
        : "-"
  }
];

const CompareProductsPage = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const [products, setProducts] = useState<ProductDetail[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const compareSlugs = useMemo(() => {
    const idsString = searchParams.get("ids") || "";
    return idsString
      .split(",")
      .map((value) => value.trim())
      .filter(Boolean)
      .slice(0, 3);
  }, [searchParams]);

  useEffect(() => {
    const loadProducts = async () => {
      if (compareSlugs.length === 0) {
        setProducts([]);
        setError(null);
        setLoading(false);
        return;
      }

      setLoading(true);
      setError(null);

      try {
        const fetchedProducts = await Promise.all(
          compareSlugs.map((productSlug) => productAPI.getDetail(productSlug))
        );
        setProducts(fetchedProducts);
      } catch (fetchError) {
        setProducts([]);
        setError(fetchError instanceof Error ? fetchError.message : "Không thể tải sản phẩm để so sánh.");
      } finally {
        setLoading(false);
      }
    };

    void loadProducts();
  }, [compareSlugs]);

  const handleRemove = (removedSlug: string) => {
    const updatedIds = compareSlugs.filter((slug) => slug !== removedSlug);
    if (updatedIds.length > 0) {
      setSearchParams({ ids: updatedIds.join(",") }, { replace: true });
    } else {
      setSearchParams({}, { replace: true });
    }
  };

  return (
    <div className="pt-20">
      <div className="mx-auto max-w-7xl px-6 py-16 md:px-12">
        <div className="mb-10 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="mb-2 text-sm font-bold uppercase tracking-widest text-primary">So sánh sản phẩm</p>
            <h1 className="text-4xl font-bold">So sánh chi tiết các mẫu đã chọn</h1>
            <p className="mt-2 max-w-2xl text-sm text-muted-foreground">
              Chọn tối thiểu 2 sản phẩm để so sánh. Dữ liệu bao gồm giá bán, thông số và tồn kho.
            </p>
          </div>
          <Link
            to="/products"
            className="inline-flex items-center justify-center rounded-full border border-outline-variant bg-white px-5 py-3 text-sm font-bold text-stone-700 transition hover:bg-stone-100"
          >
            Quay lại cửa hàng
          </Link>
        </div>

        {loading ? (
          <div className="rounded-3xl border border-stone-200 bg-white p-10 text-center text-muted-foreground">
            Đang tải dữ liệu so sánh...
          </div>
        ) : error ? (
          <div className="rounded-3xl border border-red-200 bg-red-50 p-10 text-center text-red-600">
            {error}
          </div>
        ) : compareSlugs.length < 2 ? (
          <div className="rounded-3xl border border-stone-200 bg-white p-10 text-center text-stone-700">
            Vui lòng chọn ít nhất 2 sản phẩm để so sánh.
          </div>
        ) : (
          <div className="overflow-hidden rounded-3xl border border-stone-200 bg-white shadow-sm">
            <div className="grid grid-cols-[220px_repeat(3,minmax(0,1fr))] gap-0 border-b border-stone-200 text-left text-sm">
              <div className="border-r border-stone-200 bg-stone-50 px-6 py-4 font-semibold">Thuộc tính</div>
              {products.map((product) => (
                <div key={product.id} className="px-6 py-4">
                  <div className="mb-4 flex items-start gap-4">
                    <img
                      src={resolveProductImage(product.images?.[0] || "")}
                      alt={product.name}
                      className="h-24 w-24 rounded-3xl object-cover"
                      onError={(event) => attachImageFallback(event, product.name)}
                      referrerPolicy="no-referrer"
                    />
                    <div>
                      <h2 className="text-lg font-bold">{product.name}</h2>
                      <p className="mt-1 text-xs uppercase tracking-[0.18em] text-muted-foreground">
                        {product.category?.name || "E-BIKE"}
                      </p>
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => handleRemove(product.slug)}
                    className="rounded-full border border-stone-200 bg-stone-50 px-3 py-2 text-xs font-semibold text-stone-600 transition hover:bg-stone-100"
                  >
                    Xóa
                  </button>
                </div>
              ))}
            </div>

            {compareRows.map((row) => (
              <div key={row.label} className="grid grid-cols-[220px_repeat(3,minmax(0,1fr))] gap-0 border-b border-stone-200 text-sm">
                <div className="border-r border-stone-200 bg-stone-50 px-6 py-4 font-semibold text-stone-700">{row.label}</div>
                {products.map((product) => (
                  <div key={product.id} className="px-6 py-4 text-stone-700">
                    {row.value(product)}
                  </div>
                ))}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default CompareProductsPage;
