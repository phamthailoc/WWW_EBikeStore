import { useEffect, useMemo, useState } from "react";
import {
  ArrowRight,
  BatteryCharging,
  Bike,
  ShieldCheck,
  Zap,
  Leaf,
  Gauge,
  MapPin,
  Star,
  Mail,
  Check,
  TrendingUp,
  Users,
  Smartphone,
  Wind
} from "lucide-react";
import { Link } from "react-router-dom";
import { productAPI } from "@ebike/shared-code/api";
import type { Product } from "@ebike/shared-code/types";
import { attachImageFallback, resolveProductImage } from "../utils/media";

const HERO_FALLBACK_IMAGE = "/images/hero-fallback.svg";
const HERO_BANNER_IMAGE = "https://kinetic-s3-bucket.s3.ap-southeast-1.amazonaws.com/products/Kinetic-Banner.jpg";

const HomePage = () => {
  const [products, setProducts] = useState<Product[]>([]);
  const [email, setEmail] = useState("");
  const [subscribed, setSubscribed] = useState(false);

  useEffect(() => {
    const loadProducts = async () => {
      try {
        const data = await productAPI.list();
        setProducts(data);
      } catch {
        setProducts([]);
      }
    };

    void loadProducts();
  }, []);

  const featuredProducts = useMemo(() => {
    const featured = products.filter((item) => item.featured);
    const source = featured.length >= 2 ? featured : products;
    return source.slice(0, 2);
  }, [products]);

  const allProducts = useMemo(() => {
    return products.slice(0, 4);
  }, [products]);

  const categoryCards = useMemo(() => {
    const first = featuredProducts[0];
    const second = featuredProducts[1];

    return [
      {
        title: first?.category?.name || "Xe máy điện",
        label: "Hiệu năng & Sức mạnh",
        text: first?.description || "Trải nghiệm tốc độ và công nghệ đột phá trên mọi cung đường.",
        image: first?.images?.[0] || HERO_FALLBACK_IMAGE,
        to: first ? `/product/${first.slug}` : "/products"
      },
      {
        title: second?.category?.name || "Xe đạp điện",
        label: "Linh hoạt & Thông minh",
        text: second?.description || "Giải pháp tối ưu cho di chuyển đô thị nhẹ nhàng, bền bỉ và thời trang.",
        image: second?.images?.[0] || HERO_FALLBACK_IMAGE,
        to: second ? `/product/${second.slug}` : "/products"
      }
    ];
  }, [featuredProducts]);

  const handleNewsletterSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (email) {
      setSubscribed(true);
      setEmail("");
      setTimeout(() => setSubscribed(false), 3000);
    }
  };

  return (
    <div className="flex flex-col">
      {/* Hero Section */}
      <section className="relative flex min-h-screen items-center overflow-hidden pt-20">
        <div className="absolute inset-0 z-0">
          <img
            className="h-full w-full object-cover"
            src={HERO_BANNER_IMAGE}
            alt="Kinetic Hero"
            onError={(event) => attachImageFallback(event, "Kinetic Hero", HERO_FALLBACK_IMAGE)}
            referrerPolicy="no-referrer"
          />
          <div className="absolute inset-0 bg-gradient-to-r from-background/78 via-background/36 to-transparent" />
        </div>

        <div className="relative z-10 mx-auto w-full max-w-7xl px-6 md:px-12">
          <div className="max-w-3xl">
            <p className="mono-label mb-4 text-primary animate-fade-in">The Future of Personal Mobility</p>
            <h1 className="mb-8 text-6xl font-bold leading-[0.9] tracking-tighter md:text-8xl animate-fade-in-up" style={{ animationDelay: "0.1s" }}>
              Khai phá Kỷ nguyên <br /> <span className="text-primary">Di chuyển Xanh</span>
            </h1>
            <p className="mb-10 max-w-md text-lg leading-relaxed text-muted-foreground animate-fade-in-up" style={{ animationDelay: "0.2s" }}>
              Từ những hành trình đô thị linh hoạt đến những chuyến đi xa đầy phong cách. Khám phá dòng xe điện thế hệ mới
              dành cho nhịp sống hiện đại.
            </p>
            <div className="flex flex-wrap gap-4 animate-fade-in-up" style={{ animationDelay: "0.3s" }}>
              <Link to={featuredProducts[0] ? `/product/${featuredProducts[0].slug}` : "/products"} className="btn-primary group">
                Khám phá sản phẩm nổi bật
                <Bike className="transition-transform group-hover:translate-x-1" />
              </Link>
              <Link to="/products" className="btn-secondary group">
                Xem tất cả sản phẩm
                <Bike className="transition-transform group-hover:translate-x-1" />
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* Featured Products Section */}
      <section className="bg-surface-container-lowest px-6 py-24 md:px-12">
        <div className="mx-auto max-w-7xl">
          <div className="mb-16 text-center">
            <h2 className="mb-4 text-4xl font-bold">Sản phẩm nổi bật của Kinetic</h2>
            <div className="mx-auto h-1 w-20 bg-primary" />
          </div>

          <div className="grid grid-cols-1 gap-8 md:grid-cols-2">
            {categoryCards.map((item) => (
              <Link key={item.title + item.to} to={item.to} className="group relative h-[600px] overflow-hidden rounded-2xl">
                <img
                  className="absolute inset-0 h-full w-full object-cover transition-transform duration-700 group-hover:scale-105"
                  src={resolveProductImage(item.image)}
                  alt={item.title}
                  onError={(event) => attachImageFallback(event, item.title)}
                  referrerPolicy="no-referrer"
                />
                <div className="absolute inset-0 bg-gradient-to-t from-black/90 via-black/20 to-transparent" />
                <div className="absolute bottom-0 left-0 p-12 text-white">
                  <span className="mono-label mb-4 inline-block rounded bg-white/10 px-3 py-1 text-primary backdrop-blur-md">
                    {item.label}
                  </span>
                  <h3 className="mb-6 text-5xl font-bold">{item.title}</h3>
                  <p className="mb-8 max-w-xs text-lg text-white/70 line-clamp-4">{item.text}</p>
                  <span className="inline-flex rounded bg-white px-8 py-3 font-bold text-primary transition-all duration-300 group-hover:bg-primary group-hover:text-white">
                    Xem chi tiết
                  </span>
                </div>
              </Link>
            ))}
          </div>
        </div>
      </section>

      {/* Technology Section */}
      <section className="overflow-hidden bg-surface-container-low py-24">
        <div className="mx-auto grid max-w-7xl grid-cols-1 items-center gap-20 px-6 md:px-12 lg:grid-cols-2">
          <div className="relative">
            <div className="absolute -left-10 -top-10 h-40 w-40 rounded-full bg-primary/5 blur-3xl" />
            <img
              className="relative z-10 rounded-2xl shadow-2xl"
              src={resolveProductImage(featuredProducts[0]?.images?.[0]) || HERO_FALLBACK_IMAGE}
              alt="Technology"
              onError={(event) => attachImageFallback(event, featuredProducts[0]?.name || "Kinetic Technology", HERO_FALLBACK_IMAGE)}
              referrerPolicy="no-referrer"
            />
            <div className="absolute -bottom-6 -right-6 z-20 max-w-xs rounded-xl bg-surface-container-lowest p-8 shadow-lg">
              <div className="mb-4 flex items-center gap-4">
                <Zap className="text-primary" fill="currentColor" />
                <span className="text-xl font-bold">GTR Engine</span>
              </div>
              <p className="text-sm leading-relaxed text-muted-foreground">
                Động cơ nam châm vĩnh cửu thế hệ mới, tối ưu cho sự êm ái, mạnh mẽ và tiết kiệm năng lượng.
              </p>
            </div>
          </div>

          <div className="space-y-12">
            <h2 className="text-5xl font-bold leading-tight">
              Công nghệ độc quyền <br />
              <span className="text-primary">Dẫn đầu hiệu năng</span>
            </h2>

            <div className="space-y-8">
              <div className="flex items-start gap-6">
                <div className="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-lg bg-primary/10">
                  <BatteryCharging className="text-primary" />
                </div>
                <div>
                  <h4 className="mb-2 text-xl font-bold">Lithium Max Core</h4>
                  <p className="text-muted-foreground">
                    Pin lithium mật độ cao với hệ thống quản lý nhiệt thông minh, giúp tăng độ bền và thời gian sử dụng.
                  </p>
                </div>
              </div>

              <div className="flex items-start gap-6">
                <div className="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-lg bg-primary/10">
                  <ShieldCheck className="text-primary" />
                </div>
                <div>
                  <h4 className="mb-2 text-xl font-bold">Hệ điều hành Kinetic OS</h4>
                  <p className="text-muted-foreground">
                    Kết nối thông minh, định vị thời gian thực và cập nhật phần mềm từ xa cho toàn bộ hệ sinh thái xe.
                  </p>
                </div>
              </div>
            </div>

            <Link to="/products" className="inline-flex items-center gap-2 border-b-2 border-primary pb-1 font-bold text-primary">
              Khám phá công nghệ GTR
              <ArrowRight size={16} />
            </Link>
          </div>
        </div>
      </section>

      {/* Key Features Section */}
      <section className="bg-surface-container-lowest px-6 py-24 md:px-12">
        <div className="mx-auto max-w-7xl">
          <div className="mb-16 text-center">
            <h2 className="mb-4 text-4xl font-bold">Tại sao chọn Kinetic?</h2>
            <p className="text-lg text-muted-foreground">Những đặc điểm vượt trội giúp bạn di chuyển thông minh</p>
          </div>

          <div className="grid grid-cols-1 gap-8 md:grid-cols-2 lg:grid-cols-4">
            {[
              {
                icon: <Leaf className="h-8 w-8 text-primary" />,
                title: "Thân thiện môi trường",
                description: "Zero khí thải, giúp bảo vệ hành tinh chúng ta"
              },
              {
                icon: <Gauge className="h-8 w-8 text-primary" />,
                title: "Hiệu suất tối ưu",
                description: "Công nghệ GTR giúp tiết kiệm năng lượng tối đa"
              },
              {
                icon: <MapPin className="h-8 w-8 text-primary" />,
                title: "Định vị toàn cầu",
                description: "GPS thông minh với hỗ trợ bản đồ offline"
              },
              {
                icon: <Wind className="h-8 w-8 text-primary" />,
                title: "Trọng lượng nhẹ",
                description: "Thiết kế tối ưu cho sự linh hoạt và tiện lợi"
              }
            ].map((feature, index) => (
              <div
                key={index}
                className="group rounded-xl border border-border bg-surface-container p-8 transition-all duration-300 hover:border-primary hover:shadow-lg hover:shadow-primary/10"
              >
                <div className="mb-4 inline-flex rounded-lg bg-primary/10 p-3 transition-all group-hover:bg-primary/20">
                  {feature.icon}
                </div>
                <h4 className="mb-3 text-xl font-bold">{feature.title}</h4>
                <p className="text-muted-foreground">{feature.description}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Products Grid Section */}
      {allProducts.length > 0 && (
        <section className="bg-surface-container-low px-6 py-24 md:px-12">
          <div className="mx-auto max-w-7xl">
            <div className="mb-16 flex items-end justify-between">
              <div>
                <h2 className="mb-4 text-4xl font-bold">Khám phá bộ sưu tập</h2>
                <div className="h-1 w-20 bg-primary" />
              </div>
              <Link to="/products" className="hidden text-sm font-bold text-primary hover:text-primary/80 md:block">
                Xem tất cả →
              </Link>
            </div>

            <div className="grid grid-cols-1 gap-8 md:grid-cols-2 lg:grid-cols-4">
              {allProducts.map((product) => (
                <Link
                  key={product.id}
                  to={`/product/${product.slug}`}
                  className="group overflow-hidden rounded-xl border border-border transition-all duration-300 hover:border-primary hover:shadow-lg hover:shadow-primary/10"
                >
                  <div className="relative h-64 overflow-hidden bg-surface-container">
                    <img
                      src={resolveProductImage(product.images?.[0]) || HERO_FALLBACK_IMAGE}
                      alt={product.name}
                      className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-110"
                      onError={(e) => attachImageFallback(e, product.name)}
                      referrerPolicy="no-referrer"
                    />
                  </div>
                  <div className="p-6">
                    <p className="mb-2 text-sm font-medium text-primary">{product.category?.name}</p>
                    <h4 className="mb-3 font-bold line-clamp-2 group-hover:text-primary">{product.name}</h4>
                    <p className="mb-4 text-sm text-muted-foreground line-clamp-2">{product.description}</p>
                    <div className="flex items-center justify-between pt-4 border-t border-border">
                      <span className="text-lg font-bold text-primary">{product.price?.toLocaleString()}đ</span>
                      <Bike className="h-4 w-4 opacity-0 transition-all group-hover:opacity-100" />
                    </div>
                  </div>
                </Link>
              ))}
            </div>

            <Link to="/products" className="mt-12 flex justify-center">
              <button className="btn-primary group">
                Xem tất cả sản phẩm
                <ArrowRight className="transition-transform group-hover:translate-x-1" />
              </button>
            </Link>
          </div>
        </section>
      )}

      {/* Stats Section */}
      <section className="bg-gradient-to-r from-primary/5 via-transparent to-primary/5 px-6 py-16 md:px-12">
        <div className="mx-auto max-w-7xl">
          <div className="grid grid-cols-2 gap-8 md:grid-cols-4">
            {[
              { label: "Khách hàng", value: "10K+" },
              { label: "Xe bán ra", value: "25K+" },
              { label: "Quốc gia", value: "15+" },
              { label: "Năm kinh nghiệm", value: "8+" }
            ].map((stat, index) => (
              <div key={index} className="text-center">
                <div className="mb-2 text-3xl font-bold text-primary">{stat.value}</div>
                <p className="text-sm text-muted-foreground">{stat.label}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Testimonials Section */}
      <section className="bg-surface-container-lowest px-6 py-24 md:px-12">
        <div className="mx-auto max-w-7xl">
          <div className="mb-16 text-center">
            <h2 className="mb-4 text-4xl font-bold">Khách hàng nói gì về Kinetic?</h2>
            <p className="text-lg text-muted-foreground">Những bình luận thực từ những người dùng hài lòng</p>
          </div>

          <div className="grid grid-cols-1 gap-8 md:grid-cols-3">
            {[
              {
                name: "Nguyễn Văn A",
                role: "Chuyên viên CNTT",
                content: "Kinetic mang đến trải nghiệm tuyệt vời. Xe chạy êm, nhanh và pin kéo dài cả ngày.",
                rating: 5
              },
              {
                name: "Trần Thị B",
                role: "Freelancer",
                content: "Chiếc xe này giúp tôi tiết kiệm thời gian và chi phí đi lại. Thiết kế đẹp và hiện đại!",
                rating: 5
              },
              {
                name: "Lê Hoàng C",
                role: "Business Owner",
                content: "Đầu tư tốt nhất cho công ty. Nhân viên yêu thích chiếc xe và nó giúp quảng bá thương hiệu.",
                rating: 5
              }
            ].map((testimonial, index) => (
              <div key={index} className="rounded-xl bg-surface-container p-8 shadow-sm">
                <div className="mb-4 flex gap-1">
                  {Array(testimonial.rating)
                    .fill(0)
                    .map((_, i) => (
                      <Star key={i} className="h-5 w-5 fill-primary text-primary" />
                    ))}
                </div>
                <p className="mb-6 text-lg text-foreground">{testimonial.content}</p>
                <div>
                  <p className="font-bold">{testimonial.name}</p>
                  <p className="text-sm text-muted-foreground">{testimonial.role}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

    

      {/* CTA Section */}
      <section className="relative overflow-hidden bg-primary px-6 py-20 md:px-12">
        <div className="absolute inset-0 opacity-10">
          <div className="absolute -bottom-20 -right-20 h-96 w-96 rounded-full bg-white/20 blur-3xl" />
        </div>

        <div className="relative mx-auto max-w-4xl text-center text-white">
          <h2 className="mb-6 text-4xl font-bold md:text-5xl">Sẵn sàng cho cuộc hành trình của bạn?</h2>
          <p className="mb-10 text-lg text-white/80">
            Tham gia hàng nghìn khách hàng đã trải nghiệm tương lai của di chuyển cá nhân
          </p>
          <div className="flex flex-col gap-4 sm:flex-row sm:justify-center">
            <Link to="/products" className="inline-flex items-center justify-center rounded-lg bg-white px-8 py-4 font-bold text-primary transition-all hover:bg-white/90">
              Mua ngay
              <Bike className="ml-2 h-5 w-5" />
            </Link>
            <button className="inline-flex items-center justify-center rounded-lg border-2 border-white px-8 py-4 font-bold text-white transition-all hover:bg-white/10">
              Liên hệ tư vấn
              <ArrowRight className="ml-2 h-5 w-5" />
            </button>
          </div>
        </div>
      </section>

      {/* Newsletter Section */}
      <section className="bg-surface-container-lowest px-6 py-20 md:px-12">
        <div className="mx-auto max-w-2xl">
          <div className="rounded-2xl border border-border bg-surface-container p-12">
            <div className="text-center">
              <Smartphone className="mx-auto mb-4 h-8 w-8 text-primary" />
              <h2 className="mb-3 text-3xl font-bold">Nhận thông tin mới nhất</h2>
              <p className="mb-8 text-muted-foreground">
                Đăng ký để nhận những cập nhật sản phẩm, ưu đãi độc quyền và lời khuyên về e-mobility
              </p>

              <form onSubmit={handleNewsletterSubmit} className="flex flex-col gap-3 sm:flex-row">
                <div className="relative flex-1">
                  <Mail className="absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-muted-foreground pointer-events-none" />
                  <input
                    type="email"
                    placeholder="Nhập email của bạn"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                    className="w-full rounded-lg border border-border bg-background py-3 pl-12 pr-4 transition-all focus:border-primary focus:outline-none"
                  />
                </div>
                <button
                  type="submit"
                  className="btn-primary whitespace-nowrap"
                >
                  {subscribed ? "✓ Đã đăng ký!" : "Đăng ký"}
                </button>
              </form>

              {subscribed && (
                <p className="mt-4 text-sm text-primary font-medium">Cảm ơn! Kiểm tra email của bạn để xác nhận.</p>
              )}
            </div>
          </div>
        </div>
      </section>
    </div>
  );
};

export default HomePage;