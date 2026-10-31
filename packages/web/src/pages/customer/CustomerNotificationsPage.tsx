import { useEffect, useState } from "react";
import { notificationAPI } from "@ebike/shared-code/api";
import type { UserNotification } from "@ebike/shared-code/types";
import SectionShell from "../../components/common/SectionShell";

const typeLabel: Record<string, string> = {
  ORDER_STATUS: "Đơn hàng",
  PAYMENT_SUCCESS: "Thanh toán thành công",
  PAYMENT_FAILED: "Thanh toán thất bại"
};

const replaceText = (value: string, search: string, replacement: string) => value.split(search).join(replacement);

const normalizeNotificationText = (value: string) =>
  [
    ["Don hang", "Đơn hàng"],
    ["Thanh toan", "Thanh toán"],
    ["Cap nhat don hang", "Cập nhật đơn hàng"],
    ["Da nhan don hang", "Đã nhận đơn hàng"],
    ["thanh cong", "thành công"],
    ["that bai", "thất bại"],
    [" da duoc tao va dang cho xac nhan", " đã được tạo và đang chờ xác nhận"],
    [" da chuyen sang trang thai PENDING", " đã chuyển sang trạng thái chờ xác nhận"],
    [" da chuyen sang trang thai CONFIRMED", " đã chuyển sang trạng thái đã xác nhận"],
    [" da chuyen sang trang thai CANCELLATION_REQUESTED", " đã chuyển sang trạng thái đang chờ duyệt hủy"],
    [" da chuyen sang trang thai PROCESSING", " đã chuyển sang trạng thái đang xử lý"],
    [" da chuyen sang trang thai SHIPPED", " đã chuyển sang trạng thái đang giao"],
    [" da chuyen sang trang thai DELIVERED", " đã chuyển sang trạng thái đã giao"],
    [" da chuyen sang trang thai CANCELLED", " đã chuyển sang trạng thái đã hủy"],
    [" da thanh cong", " đã thành công"],
    [" chua thanh cong", " chưa thành công"],
    ["Vui long thu lai hoac chon hinh thuc khac", "Vui lòng thử lại hoặc chọn hình thức khác"]
  ].reduce((text, [search, replacement]) => replaceText(text, search, replacement), value);

const CustomerNotificationsPage = () => {
  const [notifications, setNotifications] = useState<UserNotification[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadNotifications = async () => {
    setLoading(true);
    setError("");
    try {
      setNotifications(await notificationAPI.list());
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Không thể tải thông báo.");
      setNotifications([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadNotifications();
  }, []);

  const markAsRead = async (notification: UserNotification) => {
    if (notification.read) {
      return;
    }
    try {
      const updated = await notificationAPI.markAsRead(notification.id);
      setNotifications((current) => current.map((item) => (item.id === updated.id ? updated : item)));
    } catch {
      await loadNotifications();
    }
  };

  const markAllAsRead = async () => {
    try {
      await notificationAPI.markAllAsRead();
      await loadNotifications();
    } catch (markError) {
      setError(markError instanceof Error ? markError.message : "Không thể đánh dấu thông báo.");
    }
  };

  return (
    <SectionShell
      eyebrow="Thông báo"
      title="Cập nhật mới nhất về đơn hàng và thanh toán."
      description="Theo dõi các thay đổi trạng thái đơn hàng, kết quả thanh toán và đánh dấu thông báo đã đọc."
    >
      <div className="px-6 py-8">
        <div className="mb-5 flex justify-end">
          <button type="button" onClick={markAllAsRead} className="btn-secondary text-sm">
            Đánh dấu tất cả đã đọc
          </button>
        </div>

        {loading ? (
          <div className="rounded-lg bg-gray-50 p-6 text-center text-gray-600">Đang tải thông báo...</div>
        ) : error ? (
          <div className="rounded-lg border border-red-200 bg-red-50 p-6 text-center text-red-700">{error}</div>
        ) : notifications.length === 0 ? (
          <div className="rounded-lg bg-gray-50 p-6 text-center text-gray-600">Bạn chưa có thông báo nào.</div>
        ) : (
          <div className="space-y-3">
            {notifications.map((notification) => (
              <button
                key={notification.id}
                type="button"
                onClick={() => void markAsRead(notification)}
                className={`w-full rounded-lg border p-5 text-left transition hover:bg-black/5 ${
                  notification.read ? "border-outline-variant/15 bg-white" : "border-primary/25 bg-primary/5"
                }`}
              >
                <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                  <div>
                    <span className="inline-block rounded-full bg-white px-3 py-1 text-xs font-semibold text-primary">
                      {typeLabel[notification.type] || notification.type}
                    </span>
                    <h2 className="mt-3 text-xl font-bold text-foreground">{normalizeNotificationText(notification.title)}</h2>
                    <p className="mt-2 leading-6 text-muted-foreground">{normalizeNotificationText(notification.message)}</p>
                  </div>
                  <div className="text-sm text-muted-foreground">
                    {new Date(notification.createdAt).toLocaleString("vi-VN")}
                  </div>
                </div>
              </button>
            ))}
          </div>
        )}
      </div>
    </SectionShell>
  );
};

export default CustomerNotificationsPage;
