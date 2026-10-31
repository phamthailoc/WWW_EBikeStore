import { Bell } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { notificationAPI } from "@ebike/shared-code/api";
import type { UserNotification } from "@ebike/shared-code/types";

type NotificationBellProps = {
  enabled: boolean;
};

const getNotificationTone = (type: string) => {
  if (type === "PAYMENT_SUCCESS") {
    return "bg-emerald-50 text-emerald-700";
  }
  if (type === "PAYMENT_FAILED") {
    return "bg-rose-50 text-rose-700";
  }
  return "bg-blue-50 text-blue-700";
};

const notificationTypeLabel = (type: string) => (type === "ORDER_STATUS" ? "Đơn hàng" : "Thanh toán");

const replaceText = (value: string, search: string, replacement: string) => value.split(search).join(replacement);

const normalizeNotificationText = (value: string) =>
  [
    ["Thong bao", "Thông báo"],
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

const NotificationBell = ({ enabled }: NotificationBellProps) => {
  const navigate = useNavigate();
  const dropdownRef = useRef<HTMLDivElement | null>(null);
  const [open, setOpen] = useState(false);
  const [notifications, setNotifications] = useState<UserNotification[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(false);

  const latestNotifications = useMemo(() => notifications.slice(0, 5), [notifications]);

  const loadNotifications = async () => {
    if (!enabled) {
      setNotifications([]);
      setUnreadCount(0);
      return;
    }
    setLoading(true);
    try {
      const [items, count] = await Promise.all([
        notificationAPI.list(),
        notificationAPI.unreadCount()
      ]);
      setNotifications(items);
      setUnreadCount(count.unreadCount);
    } catch {
      setNotifications([]);
      setUnreadCount(0);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadNotifications();
  }, [enabled]);

  useEffect(() => {
    if (!enabled) {
      return;
    }
    const intervalId = window.setInterval(() => {
      void notificationAPI.unreadCount()
        .then((response) => setUnreadCount(response.unreadCount))
        .catch(() => undefined);
    }, 30000);
    const handleFocus = () => {
      void loadNotifications();
    };
    window.addEventListener("focus", handleFocus);
    return () => {
      window.clearInterval(intervalId);
      window.removeEventListener("focus", handleFocus);
    };
  }, [enabled]);

  useEffect(() => {
    if (!open) {
      return;
    }
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [open]);

  const handleOpen = () => {
    setOpen((current) => !current);
    if (!open) {
      void loadNotifications();
    }
  };

  const handleNotificationClick = async (notification: UserNotification) => {
    if (!notification.read) {
      try {
        await notificationAPI.markAsRead(notification.id);
        setUnreadCount((current) => Math.max(0, current - 1));
        setNotifications((current) =>
          current.map((item) => (item.id === notification.id ? { ...item, read: true, readAt: new Date().toISOString() } : item))
        );
      } catch {
        // Navigation should still work if marking the item read fails.
      }
    }
    setOpen(false);
    navigate(notification.targetUrl || "/customer/notifications");
  };

  const handleMarkAll = async () => {
    try {
      await notificationAPI.markAllAsRead();
      setUnreadCount(0);
      setNotifications((current) => current.map((item) => ({ ...item, read: true, readAt: item.readAt ?? new Date().toISOString() })));
    } catch {
      await loadNotifications();
    }
  };

  if (!enabled) {
    return null;
  }

  return (
    <div ref={dropdownRef} className="relative">
      <button
        type="button"
        onClick={handleOpen}
        className="relative inline-flex h-9 w-9 items-center justify-center rounded-full text-foreground/70 transition-colors hover:bg-black/5 hover:text-primary"
        aria-label="Thông báo"
      >
        <Bell size={18} />
        {unreadCount > 0 ? (
          <span className="absolute -right-1 -top-1 min-w-[18px] rounded-full bg-primary px-1 text-center text-[11px] font-bold leading-[18px] text-white">
            {unreadCount > 9 ? "9+" : unreadCount}
          </span>
        ) : null}
      </button>

      {open ? (
        <div className="absolute right-0 top-12 w-[340px] overflow-hidden rounded-lg border border-outline-variant/20 bg-white shadow-xl">
          <div className="flex items-center justify-between border-b border-outline-variant/10 px-4 py-3">
            <p className="font-semibold text-foreground">Thông báo</p>
            <button type="button" onClick={handleMarkAll} className="text-xs font-semibold text-primary hover:underline">
              Đánh dấu đã đọc
            </button>
          </div>
          <div className="max-h-[360px] overflow-y-auto">
            {loading ? (
              <div className="px-4 py-6 text-center text-sm text-muted-foreground">Đang tải thông báo...</div>
            ) : latestNotifications.length === 0 ? (
              <div className="px-4 py-6 text-center text-sm text-muted-foreground">Chưa có thông báo.</div>
            ) : (
              latestNotifications.map((notification) => (
                <button
                  key={notification.id}
                  type="button"
                  onClick={() => void handleNotificationClick(notification)}
                  className="flex w-full gap-3 border-b border-outline-variant/10 px-4 py-3 text-left transition hover:bg-black/5"
                >
                  <span className={`mt-1 h-2.5 w-2.5 rounded-full ${notification.read ? "bg-gray-200" : "bg-primary"}`} />
                  <span className="min-w-0 flex-1">
                    <span className={`mb-1 inline-block rounded-full px-2 py-0.5 text-[11px] font-semibold ${getNotificationTone(notification.type)}`}>
                      {notificationTypeLabel(notification.type)}
                    </span>
                    <span className="block font-semibold text-foreground">{normalizeNotificationText(notification.title)}</span>
                    <span className="mt-1 block text-sm leading-5 text-muted-foreground">{normalizeNotificationText(notification.message)}</span>
                  </span>
                </button>
              ))
            )}
          </div>
          <button
            type="button"
            onClick={() => {
              setOpen(false);
              navigate("/customer/notifications");
            }}
            className="w-full bg-surface-container-low px-4 py-3 text-sm font-semibold text-primary hover:bg-black/5"
          >
            Xem tất cả thông báo
          </button>
        </div>
      ) : null}
    </div>
  );
};

export default NotificationBell;
