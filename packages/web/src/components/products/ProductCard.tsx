import { AlertCircle, ArrowRight } from "lucide-react";
import { attachImageFallback, resolveProductImage } from "../../utils/media";

export type ProductCardProduct = {
  id: number;
  name: string;
  slug: string;
  description: string;
  price: number;
  discountPrice?: number | null;
  rating?: number | null;
  reviewCount?: number | null;
  stockQuantity?: number | null;
  category?: { id?: number; name?: string; slug?: string } | null;
  images?: string[] | null;
};

type ProductCardProps = {
  product: ProductCardProduct;
  onBuy: (slug: string) => void;
};

const StarRow = ({ value }: { value: number }) => (
  <div className="flex items-center gap-0.5">
    {[...Array(5)].map((_, i) => (
      <span
        key={i}
        className={`text-xs leading-none ${
          i < Math.floor(value) ? "text-amber-400" : "text-stone-200"
        }`}
      >
        ★
      </span>
    ))}
  </div>
);

const ProductCard = ({ product, onBuy }: ProductCardProps) => {
  const priceValue = product.discountPrice ?? product.price;
  const ratingValue = product.rating ?? 0;
  const reviewCount = product.reviewCount ?? 0;
  const inStock = (product.stockQuantity ?? 0) > 0;
  const category = product.category?.name ?? "Sản phẩm mới";
  const imageUrl = product.images?.[0];

  return (
    <>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Be+Vietnam+Pro:wght@400;500;600;700;800&family=Syne:wght@700;800&display=swap');
        .pc-card::before {
          content: '';
          position: absolute;
          top: 0; left: 0; right: 0;
          height: 3px;
          background: linear-gradient(90deg, #ea580c, #f97316, #fbbf24);
          opacity: 0;
          transition: opacity 0.25s;
          border-radius: 20px 20px 0 0;
        }
        .pc-card:hover::before { opacity: 1; }
        .pc-card:hover .pc-image { transform: scale(1.06); }
        .pc-card:hover .pc-name  { color: #c2410c; }
      `}</style>

      <article
        className="pc-card group relative overflow-hidden rounded-[20px] border border-stone-100 bg-white font-['Be_Vietnam_Pro',sans-serif] shadow-sm transition duration-300 hover:-translate-y-1.5 hover:shadow-2xl hover:shadow-black/10"
      >
        <div className="relative h-48 overflow-hidden bg-stone-50">
          {imageUrl ? (
            <img
              src={resolveProductImage(imageUrl)}
              alt={product.name}
              className="pc-image h-full w-full object-cover transition duration-500"
              onError={(event) => attachImageFallback(event, product.name)}
              loading="lazy"
            />
          ) : (
            <div className="flex h-full w-full items-center justify-center font-['Syne',sans-serif] text-3xl font-extrabold tracking-[0.2em] text-stone-200">
              KINETIC
            </div>
          )}

          {!inStock && (
            <div className="absolute inset-0 flex items-center justify-center bg-black/50 backdrop-blur-[2px]">
              <span className="rounded-full bg-white px-5 py-1.5 text-xs font-bold tracking-wide text-stone-900">
                Sắp ra mắt
              </span>
            </div>
          )}

          <div className="absolute bottom-0 left-4">
            <span className="inline-block rounded-t-lg border border-b-0 border-stone-200 bg-white px-3 py-1 text-[10px] font-bold uppercase tracking-[0.08em] text-amber-700">
              {category}
            </span>
          </div>
        </div>

        <div className="p-[18px]">
          <h3 className="pc-name mb-2 line-clamp-2 font-['Syne',sans-serif] text-[17px] font-extrabold leading-tight tracking-tight text-stone-900 transition duration-200">
            {product.name}
          </h3>

          <p className="mb-3 line-clamp-2 text-xs leading-relaxed text-stone-400">
            {product.description}
          </p>

          <div className="mb-3.5 flex items-center gap-1.5">
            <StarRow value={ratingValue} />
            <span className="text-[11px] text-stone-400">
              {ratingValue > 0 ? ratingValue : "0"} ({reviewCount})
            </span>
          </div>

          <div className="-mx-[18px] mb-3.5 h-px bg-stone-100" />

          <div className="mb-3 leading-tight">
            <p className="mb-0.5 text-[9px] font-bold uppercase tracking-[0.14em] text-stone-300">
              Giá bán
            </p>
            <p className="font-['Syne',sans-serif] text-[22px] font-extrabold leading-none text-orange-600">
              {priceValue.toLocaleString("vi-VN")}
              <span className="text-sm font-bold">đ</span>
            </p>
          </div>

          <button
            type="button"
            onClick={() => inStock && onBuy(product.slug)}
            disabled={!inStock}
            className={`inline-flex w-full items-center justify-center gap-2 rounded-xl py-2.5 text-xs font-bold tracking-[0.04em] transition duration-200 active:scale-95 ${
              inStock
                ? "bg-orange-600 text-white hover:bg-orange-700"
                : "cursor-not-allowed bg-stone-100 text-stone-400"
            }`}
          >
            {inStock ? (
              <>
                <ArrowRight className="h-3.5 w-3.5" />
                Mua ngay
              </>
            ) : (
              <>
                <AlertCircle className="h-3.5 w-3.5" />
                Sắp ra mắt
              </>
            )}
          </button>
        </div>
      </article>
    </>
  );
};

export default ProductCard;
