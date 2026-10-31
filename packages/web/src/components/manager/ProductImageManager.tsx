import { useEffect, useRef, useState } from "react";
import { ImagePlus, Loader2, Star, Trash2 } from "lucide-react";
import { productImageAPI } from "@ebike/shared-code/api";
import type { AdminProductImage } from "@ebike/shared-code/types";
import { attachImageFallback, resolveProductImage } from "../../utils/media";

type ProductImageManagerProps = {
  productId: number;
  productName: string;
  onClose: () => void;
};

const ProductImageManager = ({ productId, productName, onClose }: ProductImageManagerProps) => {
  const [images, setImages] = useState<AdminProductImage[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [pendingDeleteId, setPendingDeleteId] = useState<number | null>(null);
  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const loadImages = async () => {
    try {
      setLoading(true);
      setImages(await productImageAPI.listByProduct(productId));
      setError("");
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Không thể tải danh sách ảnh.");
      setImages([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadImages();
  }, [productId]);

  const handleUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = event.target.files;
    if (!files || files.length === 0) {
      return;
    }

    setUploading(true);
    setError("");
    setMessage("");

    try {
      for (const file of Array.from(files)) {
        await productImageAPI.upload(productId, file, { primaryImage: images.length === 0 });
      }
      setMessage(`Đã tải lên ${files.length} ảnh.`);
      await loadImages();
    } catch (uploadError) {
      setError(uploadError instanceof Error ? uploadError.message : "Upload ảnh thất bại.");
    } finally {
      setUploading(false);
      event.target.value = "";
    }
  };

  const handleSetPrimary = async (imageId: number) => {
    try {
      setError("");
      await productImageAPI.setPrimary(imageId);
      setMessage("Đã đặt ảnh đại diện.");
      await loadImages();
    } catch (primaryError) {
      setError(primaryError instanceof Error ? primaryError.message : "Không thể đặt ảnh đại diện.");
    }
  };

  const handleDelete = async (imageId: number) => {
    try {
      setPendingDeleteId(imageId);
      setError("");
      await productImageAPI.remove(imageId);
      setMessage("Đã xóa ảnh.");
      setConfirmDeleteId(null);
      await loadImages();
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : "Không thể xóa ảnh.");
    } finally {
      setPendingDeleteId(null);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/45 px-4 py-6">
      <div className="flex max-h-[90vh] w-full max-w-3xl flex-col overflow-hidden rounded-lg bg-white shadow-2xl">
        <div className="border-b border-slate-200 px-6 py-5">
          <p className="text-xs font-bold uppercase tracking-[0.18em] text-[#003b93]">Quản lý hình ảnh</p>
          <h3 className="mt-2 text-xl font-bold text-slate-950">{productName}</h3>
          <p className="mt-2 text-sm text-slate-500">Upload nhiều ảnh, chọn ảnh đại diện hoặc xóa ảnh không dùng.</p>
        </div>

        <div className="flex-1 overflow-y-auto px-6 py-5">
          {error && <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
          {message && <div className="mb-4 rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">{message}</div>}

          <div className="mb-5 flex flex-wrap items-center gap-3">
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              disabled={uploading}
              className="inline-flex items-center gap-2 rounded-lg bg-[#003b93] px-4 py-2.5 text-sm font-semibold text-white transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {uploading ? <Loader2 className="h-4 w-4 animate-spin" /> : <ImagePlus className="h-4 w-4" />}
              {uploading ? "Đang upload..." : "Thêm ảnh"}
            </button>
            <input ref={fileInputRef} type="file" accept="image/*" multiple className="hidden" onChange={(event) => void handleUpload(event)} />
          </div>

          {loading ? (
            <div className="py-10 text-center text-sm text-slate-500">Đang tải ảnh sản phẩm...</div>
          ) : images.length === 0 ? (
            <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 px-4 py-10 text-center text-sm text-slate-500">
              Chưa có ảnh nào. Hãy upload ít nhất một ảnh đại diện.
            </div>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2">
              {images.map((image) => (
                <article key={image.id} className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
                  <div className="relative aspect-[4/3] bg-slate-100">
                    <img
                      src={resolveProductImage(image.imageUrl)}
                      alt={image.altText || productName}
                      className="h-full w-full object-cover"
                      onError={(event) => attachImageFallback(event, productName)}
                    />
                    {image.primaryImage && (
                      <span className="absolute left-3 top-3 inline-flex items-center gap-1 rounded-full bg-[#003b93] px-2.5 py-1 text-[10px] font-bold uppercase tracking-[0.16em] text-white">
                        <Star className="h-3 w-3" />
                        Ảnh chính
                      </span>
                    )}
                  </div>
                  <div className="flex items-center justify-between gap-2 p-4">
                    <button
                      type="button"
                      onClick={() => void handleSetPrimary(image.id)}
                      disabled={Boolean(image.primaryImage)}
                      className="rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      Đặt làm ảnh chính
                    </button>
                    {confirmDeleteId === image.id ? (
                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          onClick={() => void handleDelete(image.id)}
                          disabled={pendingDeleteId === image.id}
                          className="rounded-lg bg-rose-600 px-3 py-2 text-xs font-semibold text-white"
                        >
                          {pendingDeleteId === image.id ? "Đang xóa..." : "Xác nhận"}
                        </button>
                        <button
                          type="button"
                          onClick={() => setConfirmDeleteId(null)}
                          className="rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600"
                        >
                          Hủy
                        </button>
                      </div>
                    ) : (
                      <button
                        type="button"
                        onClick={() => setConfirmDeleteId(image.id)}
                        className="inline-flex items-center gap-1 rounded-lg border border-rose-200 px-3 py-2 text-xs font-semibold text-rose-700 transition hover:bg-rose-50"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                        Xóa
                      </button>
                    )}
                  </div>
                </article>
              ))}
            </div>
          )}
        </div>

        <div className="border-t border-slate-200 px-6 py-4">
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
          >
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
};

export default ProductImageManager;
