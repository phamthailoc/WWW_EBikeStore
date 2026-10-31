import { ArrowRight, Trash2 } from "lucide-react";
import { useMemo } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useFavorites } from "@ebike/shared-code/hooks";
import { attachImageFallback, resolveProductImage } from "../utils/media";

const FavoritesPage = () => {
  const navigate = useNavigate();
  const { favorites: favoriteProducts, loading, error, isAuthenticated, removeFavorite } = useFavorites();

  const subtotal = useMemo(
    () => favoriteProducts.reduce((sum, product) => sum + (product.discountPrice ?? product.price), 0),
    [favoriteProducts]
  );

  const proceedToCheckout = () => {
    const firstProduct = favoriteProducts[0];
    if (!firstProduct) {
      return;
    }

    navigate("/checkout", {
      state: {
        product: {
          id: firstProduct.id,
          name: firstProduct.name,
          slug: firstProduct.slug,
          price: firstProduct.discountPrice ?? firstProduct.price,
          image: firstProduct.images?.[0],
          categoryName: firstProduct.category?.name
        },
        selectedColor: "Mặc định",
        quantity: 1
      }
    });
  };

  return (
    <div className="mx-auto min-h-screen max-w-7xl px-6 pb-24 pt-32 md:px-12">
      <h1 className="mb-12 text-5xl font-bold tracking-tighter">
        Danh sách <span className="text-primary">yêu thích.</span>
      </h1>

      <div className="grid grid-cols-1 gap-16 lg:grid-cols-12">
        <div className="space-y-8 lg:col-span-8">
          {loading ? (
            <div className="rounded-2xl border border-outline-variant/10 bg-white p-8 text-center text-muted-foreground shadow-sm">
              Đang tải danh sách yêu thích...
            </div>
          ) : error ? (
            <div className="rounded-2xl border border-outline-variant/20 bg-surface-container-low p-8 text-center text-muted-foreground shadow-sm">
              {error}
            </div>
          ) : favoriteProducts.length === 0 ? (
            <div className="flex flex-col items-center justify-center space-y-4 rounded-2xl border-2 border-dashed border-outline-variant/30 p-8 text-center">
              <p className="text-muted-foreground">Bạn chưa lưu sản phẩm yêu thích nào.</p>
              <Link to="/products" className="font-bold text-primary hover:underline">
                Khám phá sản phẩm
              </Link>
            </div>
          ) : (
            favoriteProducts.map((product) => (
              <div key={product.id} className="flex flex-col gap-8 rounded-2xl border border-outline-variant/10 bg-white p-8 shadow-sm sm:flex-row">
                <div className="aspect-square w-full overflow-hidden rounded-xl bg-surface-container-low sm:w-48">
                  <img
                    src={resolveProductImage(product.images?.[0])}
                    alt={product.name}
                    className="h-full w-full object-cover"
                    onError={(event) => attachImageFallback(event, product.name)}
                    referrerPolicy="no-referrer"
                  />
                </div>

                <div className="flex flex-grow flex-col justify-between py-2">
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <h3 className="mb-1 text-2xl font-bold">{product.name}</h3>
                      <p className="text-sm text-muted-foreground">{product.category?.name || "Xe điện"}</p>
                    </div>
                    <button
                      type="button"
                      onClick={() => void removeFavorite(product.id)}
                      disabled={!isAuthenticated}
                      className="text-muted-foreground transition-colors hover:text-error disabled:cursor-not-allowed disabled:opacity-50"
                      aria-label={`Xóa ${product.name} khỏi yêu thích`}
                    >
                      <Trash2 size={20} />
                    </button>
                  </div>

                  <div className="mt-8 flex items-end justify-between sm:mt-0">
                    <Link to={`/product/${product.slug}`} className="font-bold text-primary hover:underline">
                      Xem chi tiết
                    </Link>
                    <span className="text-2xl font-bold text-primary">
                      {(product.discountPrice ?? product.price).toLocaleString("vi-VN")}đ
                    </span>
                  </div>
                </div>
              </div>
            ))
          )}

          <div className="flex flex-col items-center justify-center space-y-4 rounded-2xl border-2 border-dashed border-outline-variant/30 p-8 text-center">
            <p className="text-muted-foreground">Muốn thêm mẫu xe khác vào danh sách yêu thích?</p>
            <Link to="/products" className="font-bold text-primary hover:underline">
              Tiếp tục mua sắm
            </Link>
          </div>
        </div>

        <div className="lg:col-span-4">
          <div className="sticky top-32 rounded-2xl bg-surface-container-low p-8">
            <h3 className="mb-8 text-xl font-bold">Tóm tắt yêu thích</h3>
            <div className="mb-8 space-y-4">
              <div className="flex justify-between text-sm">
                <span className="text-muted-foreground">Số sản phẩm</span>
                <span className="font-bold">{favoriteProducts.length}</span>
              </div>
              <div className="flex justify-between text-sm">
                <span className="text-muted-foreground">Tạm tính</span>
                <span className="font-bold">{subtotal.toLocaleString("vi-VN")}đ</span>
              </div>
              <div className="flex justify-between text-sm">
                <span className="text-muted-foreground">Ghi chú</span>
                <span className="font-bold text-primary">Chọn 1 sản phẩm để đặt</span>
              </div>
              <div className="my-4 h-px bg-outline-variant/20" />
              <div className="flex items-baseline justify-between">
                <span className="text-lg font-bold">Tổng</span>
                <span className="text-3xl font-bold tracking-tighter">{subtotal.toLocaleString("vi-VN")}đ</span>
              </div>
            </div>

            <button onClick={proceedToCheckout} disabled={favoriteProducts.length === 0} className="btn-primary w-full disabled:cursor-not-allowed disabled:opacity-60">
              Tiếp tục đặt hàng
              <ArrowRight size={20} />
            </button>

            <p className="mt-6 text-center text-xs leading-relaxed text-muted-foreground">
              Giá đã bao gồm VAT. Phí đăng ký biển số sẽ được tính toán ở bước tiếp theo.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default FavoritesPage;
