import type { FormEvent } from "react";
import { useEffect, useMemo, useState } from "react";
import { Battery, Edit2, Heart, RotateCcw, Star, Trash2, Zap } from "lucide-react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { productAPI, reviewsAPI } from "@ebike/shared-code/api";
import { useFavorites } from "@ebike/shared-code/hooks";
import type { Product, ProductDetail, Review } from "@ebike/shared-code/types";
import { attachImageFallback, resolveProductImage } from "../utils/media";

const ProductDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const {
    isAuthenticated,
    error: favoriteError,
    favoriteIdSet,
    isPending,
    loading: favoritesLoading,
    toggleFavorite
  } = useFavorites();

  const [product, setProduct] = useState<ProductDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedColor, setSelectedColor] = useState("");
  const [selectedImage, setSelectedImage] = useState("");
  const [reviews, setReviews] = useState<Review[]>([]);
  const [reviewsLoading, setReviewsLoading] = useState(false);
  const [reviewSaving, setReviewSaving] = useState(false);
  const [reviewError, setReviewError] = useState("");
  const [reviewMessage, setReviewMessage] = useState("");
  const [editingReviewId, setEditingReviewId] = useState<number | null>(null);
  const [reviewForm, setReviewForm] = useState({ rating: 5, title: "", comment: "" });
  const [relatedProducts, setRelatedProducts] = useState<Product[]>([]);
  const [relatedLoading, setRelatedLoading] = useState(false);
  const [relatedError, setRelatedError] = useState<string | null>(null);

  const loadFallbackRelatedProducts = async (currentProduct: ProductDetail) => {
    const products = await productAPI.list();
    const currentPrice = currentProduct.discountPrice ?? currentProduct.price;
    const lowerPrice = currentPrice * 0.7;
    const upperPrice = currentPrice * 1.3;

    return products
      .filter((item) => item.slug !== currentProduct.slug)
      .map((item) => {
        const itemPrice = item.discountPrice ?? item.price;
        let score = 0;
        if (currentProduct.category?.id && item.category?.id === currentProduct.category.id) {
          score += 5;
        }
        if (itemPrice >= lowerPrice && itemPrice <= upperPrice) {
          score += 2;
        }
        return { item, score };
      })
      .filter((candidate) => candidate.score > 0)
      .sort((first, second) => second.score - first.score || first.item.name.localeCompare(second.item.name))
      .slice(0, 4)
      .map((candidate) => candidate.item);
  };

  useEffect(() => {
    window.scrollTo(0, 0);
  }, [id]);

  useEffect(() => {
    const load = async () => {
      if (!id) {
        setError("Thiếu mã sản phẩm");
        setLoading(false);
        return;
      }

      try {
        const data = await productAPI.getDetail(id);
        setProduct(data);
        const defaultVariant = data.variants?.find((variant) => variant.defaultVariant) ?? data.variants?.[0];
        setSelectedColor(defaultVariant?.colorName || "Midnight");
        setSelectedImage(defaultVariant?.imageUrl || data.images?.[0] || "");
      } catch (fetchError) {
        setError(fetchError instanceof Error ? fetchError.message : "Không thể tải sản phẩm");
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, [id]);

  useEffect(() => {
    const loadReviews = async () => {
      if (!id) {
        return;
      }

      setReviewsLoading(true);
      try {
        setReviews(await reviewsAPI.listByProduct(id));
        setReviewError("");
      } catch (reviewLoadError) {
        setReviewError(reviewLoadError instanceof Error ? reviewLoadError.message : "Không thể tải đánh giá.");
      } finally {
        setReviewsLoading(false);
      }
    };

    const loadRelatedProducts = async () => {
      if (!id) {
        setRelatedProducts([]);
        return;
      }

      setRelatedLoading(true);
      setRelatedError(null);
      try {
        const data = await productAPI.getRelated(id, 4);
        setRelatedProducts(data.filter((item) => item.slug !== id));
      } catch (fetchError) {
        if (!product) {
          setRelatedError(fetchError instanceof Error ? fetchError.message : "Không thể tải sản phẩm liên quan");
          setRelatedProducts([]);
          return;
        }

        try {
          const fallbackData = await loadFallbackRelatedProducts(product);
          setRelatedProducts(fallbackData);
          setRelatedError(null);
        } catch (fallbackError) {
          setRelatedError(fallbackError instanceof Error ? fallbackError.message : "Không thể tải sản phẩm liên quan");
          setRelatedProducts([]);
        }
      } finally {
        setRelatedLoading(false);
      }
    };

    void loadReviews();
    void loadRelatedProducts();
  }, [id, product]);

  const variantMedia = useMemo(() => {
    if (!product?.variants?.length) {
      return [];
    }

    return product.variants.map((variant, index) => ({
      name: variant.colorName || variant.variantName || `Variant ${index + 1}`,
      code: variant.colorHex || "#cbd5e1",
      image: variant.imageUrl || product.images?.[index] || product.images?.[0] || ""
    }));
  }, [product]);

  const colors = useMemo(() => {
    if (!variantMedia.length) {
      return [
        { name: "Midnight", code: "#18181b" },
        { name: "Silver", code: "#cbd5e1" },
        { name: "Electric Blue", code: "#1d4ed8" }
      ];
    }

    return variantMedia.map(({ name, code }) => ({ name, code }));
  }, [variantMedia]);

  const reviewAverage = useMemo(() => {
    if (!reviews.length) {
      return product?.rating ?? 0;
    }
    return reviews.reduce((sum, review) => sum + review.rating, 0) / reviews.length;
  }, [product?.rating, reviews]);

  const displayedReviewCount = reviews.length || product?.reviewCount || 0;

  const relatedDisplayProducts = useMemo(
    () =>
      relatedProducts.map((item, index) => ({
        id: item.slug || String(item.id),
        name: item.name,
        type: item.category?.name || "E-BIKE",
        price: `${(item.discountPrice ?? item.price).toLocaleString("vi-VN")}đ`,
        image:
          item.images?.[0] ||
          "https://images.unsplash.com/photo-1558981806-ec527fa84c39?auto=format&fit=crop&q=80&w=600",
        badge: item.featured ? "FEATURED" : index === 0 ? "BEST SELLER" : "",
        colors: ["#1a1a1a", "#3b82f6", "#d1d5db"],
        meta: item.reviewCount ? `${item.reviewCount} đánh giá` : item.stockQuantity ? `${item.stockQuantity} trong kho` : "Sản phẩm mới"
      })),
    [relatedProducts]
  );

  if (loading) {
    return <main className="flex min-h-screen items-center justify-center text-muted-foreground">Đang tải dữ liệu sản phẩm...</main>;
  }

  if (error || !product) {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center gap-5 px-4 text-center">
        <h1 className="text-3xl font-bold">{error || "Sản phẩm không tìm thấy"}</h1>
        <button onClick={() => navigate("/products")} className="btn-primary">Quay lại sản phẩm</button>
      </main>
    );
  }

  const priceValue = product.discountPrice ?? product.price;
  const images = product.images?.length
    ? product.images
    : ["https://images.unsplash.com/photo-1558981806-ec527fa84c39?auto=format&fit=crop&q=80&w=1200"];
  const galleryImages = variantMedia.length
    ? Array.from(new Set(variantMedia.map((item) => item.image).filter(Boolean)))
    : images;
  const mainImage = selectedImage || galleryImages[0] || images[0];
  const specs = [
    { label: "Model code", value: product.specification?.modelCode || product.slug },
    { label: "Thương hiệu", value: product.specification?.brand || "YADEA" },
    { label: "Loại xe", value: product.specification?.vehicleType || product.category?.name || "E-Bike" },
    { label: "Quãng đường tối đa", value: product.specification?.maxRangeKm ? `${product.specification.maxRangeKm} km` : "150 km" },
    { label: "Vận tốc tối đa", value: product.specification?.maxSpeedKmh ? `${product.specification.maxSpeedKmh} km/h` : "95 km/h" },
    { label: "Công suất động cơ", value: product.specification?.motorPowerWatts ? `${product.specification.motorPowerWatts} W` : "5000 W" },
    { label: "Thời gian sạc đầy", value: product.specification?.chargingTimeHours ? `${product.specification.chargingTimeHours} giờ` : "3 - 4 giờ" },
    { label: "Loại pin", value: product.specification?.batteryType || "Lithium-ion NMC" },
    { label: "Dung lượng pin", value: product.specification?.batteryCapacityAh ? `${product.specification.batteryCapacityAh} Ah` : "60 Ah" },
    { label: "Hệ thống phanh", value: product.specification?.brakeType || "Disc brake" },
    { label: "Truyền động", value: product.specification?.driveType || "Hub drive" },
    { label: "Bảo hành", value: product.specification?.warrantyMonths ? `${product.specification.warrantyMonths} tháng` : "60 tháng" },
    { label: "Tính năng thông minh", value: product.specification?.smartFeatures || "Bảng điều khiển thông minh và kết nối ứng dụng" }
  ];

  const handleToggleFavorite = async () => {
    if (!product?.id || favoritesLoading || isPending(product.id)) {
      return;
    }

    if (!isAuthenticated) {
      navigate("/auth");
      return;
    }

    await toggleFavorite(product.id);
  };

  const handleBuyNow = () => {
    navigate("/checkout", {
      state: {
        product: {
          id: Number(product.id),
          name: product.name,
          slug: product.slug,
          price: priceValue,
          image: mainImage,
          categoryName: product.category?.name || "E-Bike"
        },
        selectedColor,
        quantity: 1
      }
    });
  };

  const resetReviewForm = () => {
    setReviewForm({ rating: 5, title: "", comment: "" });
    setEditingReviewId(null);
  };

  const handleReviewSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!id || reviewSaving) {
      return;
    }
    if (!isAuthenticated) {
      setReviewError("Vui lòng đăng nhập để viết đánh giá.");
      return;
    }

    const payload = {
      rating: reviewForm.rating,
      title: reviewForm.title.trim() || undefined,
      comment: reviewForm.comment.trim() || undefined
    };

    setReviewSaving(true);
    setReviewError("");
    setReviewMessage("");
    try {
      const savedReview = editingReviewId
        ? await reviewsAPI.update(editingReviewId, payload)
        : await reviewsAPI.create(id, payload);

      setReviews((current) => {
        const existingIndex = current.findIndex((review) => review.id === savedReview.id);
        if (existingIndex === -1) {
          return [savedReview, ...current];
        }
        return current.map((review) => (review.id === savedReview.id ? savedReview : review));
      });
      setReviewMessage(editingReviewId ? "Đã cập nhật đánh giá." : "Đã gửi đánh giá của bạn.");
      resetReviewForm();
    } catch (submitError) {
      setReviewError(submitError instanceof Error ? submitError.message : "Không thể lưu đánh giá.");
    } finally {
      setReviewSaving(false);
    }
  };

  const startEditReview = (review: Review) => {
    setEditingReviewId(review.id);
    setReviewForm({
      rating: review.rating,
      title: review.title ?? "",
      comment: review.comment ?? ""
    });
    setReviewError("");
    setReviewMessage("");
  };

  const handleDeleteReview = async (reviewId: number) => {
    if (!window.confirm("Xóa đánh giá này?")) {
      return;
    }

    setReviewSaving(true);
    setReviewError("");
    setReviewMessage("");
    try {
      await reviewsAPI.remove(reviewId);
      setReviews((current) => current.filter((review) => review.id !== reviewId));
      setReviewMessage("Đã xóa đánh giá.");
      if (editingReviewId === reviewId) {
        resetReviewForm();
      }
    } catch (deleteError) {
      setReviewError(deleteError instanceof Error ? deleteError.message : "Không thể xóa đánh giá.");
    } finally {
      setReviewSaving(false);
    }
  };

  return (
    <div className="pb-20 pt-24">
      <section className="mx-auto grid max-w-7xl grid-cols-1 items-start gap-16 px-6 py-12 lg:grid-cols-12 lg:px-12 lg:py-20">
        <div className="space-y-6 lg:col-span-7">
          <div className="group relative aspect-[16/10] overflow-hidden rounded-xl bg-surface-container-low">
            <img
              className="h-full w-full object-cover"
              src={resolveProductImage(mainImage)}
              alt={product.name}
              onError={(event) => attachImageFallback(event, product.name)}
              referrerPolicy="no-referrer"
            />
            <div className="absolute bottom-6 left-1/2 flex -translate-x-1/2 items-center gap-4 rounded-full border border-white/20 bg-white/40 px-6 py-3 backdrop-blur-md">
              <RotateCcw size={16} />
              <span className="mono-label text-foreground">Tương tác để xoay</span>
            </div>
          </div>

          <div className="grid grid-cols-4 gap-4">
            {galleryImages.slice(0, 4).map((image, index) => (
              <button
                type="button"
                key={`${image}-${index}`}
                onClick={() => setSelectedImage(image)}
                className={`aspect-square overflow-hidden rounded-lg transition-all ${mainImage === image ? "ring-2 ring-primary" : "opacity-60 hover:opacity-100"}`}
              >
                <img
                  className="h-full w-full object-cover"
                  src={resolveProductImage(image)}
                  alt={`${product.name} ${index + 1}`}
                  onError={(event) => attachImageFallback(event, product.name)}
                  referrerPolicy="no-referrer"
                />
              </button>
            ))}
          </div>
        </div>

        <div className="space-y-10 lg:col-span-5">
          <div className="space-y-4">
            <span className="mono-label text-primary">{product.category?.name || "Prestige Series"}</span>
            <h1 className="text-5xl font-bold leading-none tracking-tighter lg:text-6xl">{product.name}</h1>
            <p className="text-3xl text-muted-foreground">{priceValue.toLocaleString("vi-VN")} VND</p>
          </div>

          <div className="space-y-6">
            <div className="space-y-3">
              <h3 className="mono-label text-foreground/50">Màu ngoại thất</h3>
              <div className="flex gap-4">
                {colors.map((color) => (
                  <button
                    key={color.name}
                    type="button"
                    onClick={() => {
                      setSelectedColor(color.name);
                      const nextImage = variantMedia.find((variant) => variant.name === color.name)?.image;
                      if (nextImage) {
                        setSelectedImage(nextImage);
                      }
                    }}
                    className={`h-10 w-10 rounded-full transition-all ${selectedColor === color.name ? "ring-2 ring-primary ring-offset-4" : "hover:scale-110"}`}
                    style={{ backgroundColor: color.code }}
                    aria-label={color.name}
                  />
                ))}
              </div>
            </div>

            <div className="space-y-3">
              <h3 className="mono-label text-foreground/50">Thông số nổi bật</h3>
              <ul className="space-y-2 text-sm text-muted-foreground">
                <li className="flex items-center gap-2">
                  <Zap size={18} className="text-primary" />
                  {product.specification?.motorPowerWatts
                    ? `${product.specification.motorPowerWatts}W công suất động cơ`
                    : product.specification?.smartFeatures || "Tăng tốc 0-60 km/h trong 3,9 giây"}
                </li>
                <li className="flex items-center gap-2">
                  <Battery size={18} className="text-primary" />
                  {product.specification?.maxRangeKm ? `${product.specification.maxRangeKm}km cho một lần sạc` : "150km cho một lần sạc"}
                </li>
              </ul>
            </div>
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <button className="btn-primary" onClick={handleBuyNow}>
              Mua ngay
            </button>
            <button
              type="button"
              onClick={handleToggleFavorite}
              disabled={favoritesLoading || (product ? isPending(product.id) : false)}
              className="btn-secondary disabled:cursor-not-allowed disabled:opacity-70"
            >
              <Heart size={20} fill={product && favoriteIdSet.has(product.id) ? "currentColor" : "none"} />
              {product && isPending(product.id)
                ? "Đang cập nhật..."
                : product && favoriteIdSet.has(product.id)
                  ? "Bỏ khỏi yêu thích"
                  : "Thêm vào yêu thích"}
            </button>
          </div>

          {favoriteError ? <p className="text-sm text-muted-foreground">{favoriteError}</p> : null}

          <div className="rounded-xl border-l-4 border-primary bg-surface-container-low p-6">
            <p className="text-sm leading-relaxed text-muted-foreground">
              Chính sách bảo hành 5 năm hoặc 50.000km. Miễn phí cứu hộ 24/7 trong năm đầu tiên sử dụng.
            </p>
          </div>
        </div>
      </section>

      <section className="bg-surface-container-low py-24">
        <div className="mx-auto max-w-7xl px-6 lg:px-12">
          <div className="mb-16">
            <h2 className="text-4xl font-bold">Tính năng đột phá</h2>
            <p className="mt-2 text-muted-foreground">Kỹ thuật chính xác gặp gỡ công nghệ tương lai.</p>
          </div>

          <div className="grid grid-cols-1 gap-6 md:grid-cols-12">
            <div className="relative overflow-hidden rounded-xl bg-white p-10 md:col-span-8">
              <div className="z-10 max-w-sm">
                <h3 className="mb-4 text-3xl font-bold">Màn hình LCD 7 inch</h3>
                <p className="leading-relaxed text-muted-foreground">
                  Giao diện điều khiển thông minh tích hợp bản đồ thời gian thực, quản lý pin và kết nối smartphone.
                </p>
              </div>
              <img
                className="absolute bottom-0 right-0 h-2/3 w-2/3 rounded-tl-3xl object-cover opacity-20"
                src="https://images.unsplash.com/photo-1581091226825-a6a2a5aee158?auto=format&fit=crop&q=80&w=800"
                alt="LCD Display"
                referrerPolicy="no-referrer"
              />
            </div>

            <div className="flex flex-col items-center justify-center space-y-6 rounded-xl bg-white p-8 text-center md:col-span-4">
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-primary/10">
                <Zap className="text-primary" size={32} />
              </div>
              <div>
                <h3 className="mb-2 text-xl font-bold">Đèn LED thông minh</h3>
                <p className="text-sm text-muted-foreground">
                  Hệ thống chiếu sáng thích ứng tự động điều chỉnh cường độ theo môi trường xung quanh.
                </p>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-4xl px-6 py-24">
        <h2 className="mb-12 text-center text-3xl font-bold">Thông số kỹ thuật chi tiết</h2>
        <div className="overflow-hidden rounded-xl border border-outline-variant/15">
          <div className="grid grid-cols-2 border-b border-outline-variant/10 bg-surface-container-low p-6">
            <span className="mono-label text-muted-foreground">Hạng mục</span>
            <span className="mono-label text-muted-foreground">Thông số</span>
          </div>
          {specs.map((spec, index) => (
            <div
              key={spec.label}
              className={`grid grid-cols-2 p-6 transition-colors hover:bg-surface-container-low ${index % 2 === 1 ? "bg-surface-container-low/30" : ""}`}
            >
              <span className="text-muted-foreground">{spec.label}</span>
              <span className="font-bold">{spec.value}</span>
            </div>
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-6 py-24 lg:px-12">
        <div className="mb-10 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <span className="mono-label text-primary">Gợi ý phù hợp</span>
            <h2 className="mt-3 text-3xl font-bold md:text-4xl">Sản phẩm liên quan</h2>
            <p className="mt-2 max-w-2xl text-muted-foreground">
              Những mẫu xe có cùng danh mục, loại xe, thương hiệu hoặc khoảng giá gần với sản phẩm bạn đang xem.
            </p>
          </div>
        </div>

        {relatedLoading ? (
          <div className="rounded-xl border border-outline-variant/15 bg-surface-container-low p-8 text-center text-muted-foreground">
            Đang tải sản phẩm liên quan...
          </div>
        ) : relatedError ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-8 text-center text-red-700">
            {relatedError}
          </div>
        ) : relatedDisplayProducts.length === 0 ? (
          <div className="rounded-xl border border-outline-variant/15 bg-surface-container-low p-8 text-center text-muted-foreground">
            Hiện chưa có sản phẩm liên quan phù hợp với mẫu xe này.
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-8 md:grid-cols-2 xl:grid-cols-3">
            {relatedDisplayProducts.map((relatedProduct) => (
              <div
                key={relatedProduct.id}
                className="group overflow-hidden rounded-2xl border border-outline-variant/10 bg-white transition-all duration-500 hover:shadow-xl"
              >
                <div className="relative aspect-[4/3] overflow-hidden bg-surface-container-low">
                  <img
                    src={resolveProductImage(relatedProduct.image)}
                    alt={relatedProduct.name}
                    className="h-full w-full object-cover transition-transform duration-700 group-hover:scale-110"
                    onError={(event) => attachImageFallback(event, relatedProduct.name)}
                    referrerPolicy="no-referrer"
                  />
                  {relatedProduct.badge ? (
                    <span className="absolute left-4 top-4 rounded-full bg-black px-3 py-1 text-[10px] font-bold tracking-widest text-white">
                      {relatedProduct.badge}
                    </span>
                  ) : null}
                </div>

                <div className="p-6">
                  <div className="mb-2 flex items-start justify-between gap-4">
                    <h3 className="text-xl font-bold transition-colors group-hover:text-primary">{relatedProduct.name}</h3>
                    <span className="font-bold text-primary">{relatedProduct.price}</span>
                  </div>
                  <p className="mb-6 text-xs uppercase tracking-wider text-muted-foreground">
                    {relatedProduct.type} | {relatedProduct.meta}
                  </p>

                  <div className="flex items-center justify-between">
                    <div className="flex gap-2">
                      {relatedProduct.colors.map((color, index) => (
                        <div
                          key={`${relatedProduct.id}-${index}`}
                          className="h-3 w-3 rounded-full border border-outline-variant/20"
                          style={{ backgroundColor: color }}
                        />
                      ))}
                    </div>
                    <Link to={`/product/${relatedProduct.id}`} className="flex items-center gap-1 text-sm font-bold">
                      XEM CHI TIẾT
                      <div className="ml-2 h-[2px] w-8 bg-primary transition-all group-hover:w-12" />
                    </Link>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      <section className="bg-white py-24">
        <div className="mx-auto max-w-7xl px-6 lg:px-12">
          <div className="mb-12 flex flex-wrap items-end justify-between gap-6">
            <div>
              <h2 className="text-4xl font-bold">Đánh giá sản phẩm</h2>
              <div className="mt-2 flex items-center gap-2">
                <div className="flex text-primary">
                  {[1, 2, 3, 4, 5].map((star) => (
                    <Star key={star} size={16} fill={star <= Math.round(reviewAverage) ? "currentColor" : "none"} />
                  ))}
                </div>
                <span className="text-sm text-muted-foreground">
                  {reviewAverage ? reviewAverage.toFixed(1) : "0.0"}/5 dựa trên {displayedReviewCount} đánh giá
                </span>
              </div>
            </div>
          </div>

          <div className="grid grid-cols-1 gap-8 lg:grid-cols-12">
            <form onSubmit={handleReviewSubmit} className="space-y-5 rounded-xl bg-surface-container-low/50 p-8 lg:col-span-5">
              <div>
                <h3 className="text-2xl font-bold">{editingReviewId ? "Sửa đánh giá" : "Viết đánh giá"}</h3>
                <p className="mt-2 text-sm text-muted-foreground">
                  {isAuthenticated ? "Chia sẻ trải nghiệm của bạn với mẫu xe này." : "Bạn cần đăng nhập để gửi đánh giá."}
                </p>
              </div>

              <div className="flex gap-2 text-primary">
                {[1, 2, 3, 4, 5].map((rating) => (
                  <button
                    key={rating}
                    type="button"
                    onClick={() => setReviewForm((current) => ({ ...current, rating }))}
                    className="transition hover:scale-110"
                    aria-label={`${rating} sao`}
                    disabled={!isAuthenticated || reviewSaving}
                  >
                    <Star size={26} fill={rating <= reviewForm.rating ? "currentColor" : "none"} />
                  </button>
                ))}
              </div>

              <input
                value={reviewForm.title}
                onChange={(event) => setReviewForm((current) => ({ ...current, title: event.target.value }))}
                maxLength={255}
                disabled={!isAuthenticated || reviewSaving}
                className="w-full rounded-lg border border-outline-variant/20 bg-white px-4 py-3 text-sm font-medium focus:border-primary focus:outline-none"
                placeholder="Tiêu đề ngắn"
              />

              <textarea
                value={reviewForm.comment}
                onChange={(event) => setReviewForm((current) => ({ ...current, comment: event.target.value }))}
                maxLength={2000}
                rows={5}
                disabled={!isAuthenticated || reviewSaving}
                className="w-full rounded-lg border border-outline-variant/20 bg-white px-4 py-3 text-sm font-medium focus:border-primary focus:outline-none"
                placeholder="Bình luận của bạn"
              />

              {reviewError ? <p className="text-sm text-red-600">{reviewError}</p> : null}
              {reviewMessage ? <p className="text-sm text-primary">{reviewMessage}</p> : null}

              <div className="flex flex-wrap gap-3">
                <button type="submit" className="btn-primary disabled:cursor-not-allowed disabled:opacity-60" disabled={!isAuthenticated || reviewSaving}>
                  {reviewSaving ? "Đang lưu..." : editingReviewId ? "Cập nhật" : "Gửi đánh giá"}
                </button>
                {editingReviewId ? (
                  <button type="button" className="btn-secondary" onClick={resetReviewForm} disabled={reviewSaving}>
                    Hủy sửa
                  </button>
                ) : null}
              </div>
            </form>

            <div className="space-y-5 lg:col-span-7">
              {reviewsLoading ? (
                <div className="rounded-xl bg-surface-container-low/50 p-8 text-sm text-muted-foreground">Đang tải đánh giá...</div>
              ) : reviews.length ? (
                reviews.map((review) => (
                  <div key={review.id} className="space-y-5 rounded-xl bg-surface-container-low/50 p-8">
                    <div className="flex flex-wrap items-start justify-between gap-4">
                      <div className="flex items-center gap-4">
                        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-surface-container-highest font-bold text-primary">
                          {(review.authorName || review.username || "K").slice(0, 1).toUpperCase()}
                        </div>
                        <div>
                          <h4 className="font-bold">{review.authorName || review.username || "Kinetic Owner"}</h4>
                          <div className="mt-1 flex text-primary">
                            {[1, 2, 3, 4, 5].map((star) => (
                              <Star key={star} size={14} fill={star <= review.rating ? "currentColor" : "none"} />
                            ))}
                          </div>
                        </div>
                      </div>
                      {review.editableByCurrentUser ? (
                        <div className="flex gap-2">
                          <button type="button" onClick={() => startEditReview(review)} className="rounded-full p-2 text-muted-foreground transition hover:bg-white hover:text-primary" aria-label="Sửa đánh giá">
                            <Edit2 size={16} />
                          </button>
                          <button type="button" onClick={() => void handleDeleteReview(review.id)} className="rounded-full p-2 text-muted-foreground transition hover:bg-white hover:text-red-600" aria-label="Xóa đánh giá">
                            <Trash2 size={16} />
                          </button>
                        </div>
                      ) : null}
                    </div>
                    {review.title ? <h5 className="text-lg font-bold">{review.title}</h5> : null}
                    {review.comment ? <p className="leading-relaxed text-muted-foreground">"{review.comment}"</p> : null}
                    <p className="text-xs text-muted-foreground">
                      {review.createdAt ? new Intl.DateTimeFormat("vi-VN").format(new Date(review.createdAt)) : "Đánh giá mới"}
                    </p>
                  </div>
                ))
              ) : (
                <div className="rounded-xl bg-surface-container-low/50 p-8 text-sm text-muted-foreground">
                  Chưa có đánh giá nào cho sản phẩm này. Hãy là người đầu tiên chia sẻ trải nghiệm.
                </div>
              )}
            </div>
          </div>
        </div>
      </section>
    </div>
  );
};

export default ProductDetailPage;