import { Bell, CheckCheck, Clock, ExternalLink } from "lucide-react";
import { notificationAPI } from "@ebike/shared-code/api";
import type { UserNotification } from "@ebike/shared-code/types";
import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";

const formatRelativeTime = (value: string) => {
  const createdAt = new Date(value).getTime();
  const diffMs = Date.now() - createdAt;
  const minute = 60 * 1000;
  const hour = 60 * minute;
  const day = 24 * hour;

  if (Number.isNaN(createdAt)) return "";
  if (diffMs < minute) return "Vừa xong";
  if (diffMs < hour) return `${Math.floor(diffMs / minute)} phút trước`;
  if (diffMs < day) return `${Math.floor(diffMs / hour)} giờ trước`;
  return `${Math.floor(diffMs / day)} ngày trước`;
};

const notificationTone = (type: string) => {
  if (type === "ORDER_CREATED") return "bg-blue-50 text-blue-700";
  if (type === "PAYMENT_STATUS_CHANGED") return "bg-emerald-50 text-emerald-700";
  return "bg-amber-50 text-amber-700";
};

const ManagerNotificationsBell = () => {
  const navigate = useNavigate();
  const panelRef = useRef<HTMLDivElement | null>(null);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [items, setItems] = useState<UserNotification[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);

  const loadNotifications = async () => {
    setLoading(true);
    try {
      const [notifications, unread] = await Promise.all([
        notificationAPI.list(),
        notificationAPI.unreadCount()
      ]);
      setItems(notifications.slice(0, 8));
      setUnreadCount(unread.unreadCount);
    } catch {
      setItems([]);
      setUnreadCount(0);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadNotifications();
    const interval = window.setInterval(() => void loadNotifications(), 30000);
    return () => window.clearInterval(interval);
  }, []);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (panelRef.current && !panelRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };

    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const handleOpen = () => {
    setOpen((current) => !current);
    if (!open) {
      void loadNotifications();
    }
  };

  const handleItemClick = async (notification: UserNotification) => {
    if (!notification.read) {
      await notificationAPI.markAsRead(notification.id);
      setUnreadCount((current) => Math.max(0, current - 1));
      setItems((current) =>
        current.map((item) => item.id === notification.id ? { ...item, read: true, readAt: new Date().toISOString() } : item)
      );
    }

    setOpen(false);
    if (notification.targetUrl) {
      navigate(notification.targetUrl);
    }
  };

  const handleMarkAllRead = async () => {
    await notificationAPI.markAllAsRead();
    setUnreadCount(0);
    setItems((current) => current.map((item) => ({ ...item, read: true, readAt: item.readAt ?? new Date().toISOString() })));
  };

  return (
    <div ref={panelRef} className="relative">
      <button
        type="button"
        onClick={handleOpen}
        className="relative flex h-11 w-11 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-600 transition hover:border-[#003b93] hover:text-[#003b93]"
        aria-label="Thông báo"
      >
        <Bell className="h-5 w-5" />
        {unreadCount > 0 ? (
          <span className="absolute -right-1.5 -top-1.5 flex min-w-5 items-center justify-center rounded-full bg-red-600 px-1.5 text-[10px] font-bold leading-5 text-white">
            {unreadCount > 9 ? "9+" : unreadCount}
          </span>
        ) : null}
      </button>

      {open ? (
        <div className="absolute right-0 top-14 z-50 w-[380px] overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xl">
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
            <div>
              <h2 className="text-sm font-bold text-slate-950">Việc cần xử lý</h2>
              <p className="text-xs text-slate-500">{unreadCount} thông báo chưa đọc</p>
            </div>
            <button
              type="button"
              onClick={() => void handleMarkAllRead()}
              disabled={unreadCount === 0}
              className="inline-flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-xs font-semibold text-[#003b93] transition hover:bg-blue-50 disabled:cursor-not-allowed disabled:text-slate-300 disabled:hover:bg-transparent"
            >
              <CheckCheck className="h-4 w-4" />
              Đã đọc
            </button>
          </div>

          <div className="max-h-[420px] overflow-y-auto">
            {loading ? (
              <div className="space-y-3 p-4">
                <div className="h-16 rounded-lg bg-slate-100" />
                <div className="h-16 rounded-lg bg-slate-100" />
              </div>
            ) : items.length === 0 ? (
              <div className="px-5 py-10 text-center">
                <Bell className="mx-auto h-8 w-8 text-slate-300" />
                <p className="mt-3 text-sm font-semibold text-slate-700">Chưa có thông báo</p>
                <p className="mt-1 text-xs text-slate-500">Các đơn hàng và thanh toán cần xử lý sẽ xuất hiện ở đây.</p>
              </div>
            ) : (
              <div className="divide-y divide-slate-100">
                {items.map((notification) => (
                  <button
                    key={notification.id}
                    type="button"
                    onClick={() => void handleItemClick(notification)}
                    className={`w-full px-4 py-3 text-left transition hover:bg-slate-50 ${
                      notification.read ? "bg-white" : "bg-blue-50/35"
                    }`}
                  >
                    <div className="flex items-start gap-3">
                      <span className={`mt-1 rounded-full px-2 py-0.5 text-[10px] font-bold ${notificationTone(notification.type)}`}>
                        {notification.type === "ORDER_CREATED"
                          ? "ĐƠN"
                          : notification.type === "PAYMENT_STATUS_CHANGED"
                            ? "TIỀN"
                            : "TRẠNG THÁI"}
                      </span>
                      <div className="min-w-0 flex-1">
                        <div className="flex items-start justify-between gap-2">
                          <p className="text-sm font-bold text-slate-950">{notification.title}</p>
                          {notification.targetUrl ? <ExternalLink className="mt-0.5 h-3.5 w-3.5 shrink-0 text-slate-400" /> : null}
                        </div>
                        <p className="mt-1 line-clamp-2 text-xs leading-5 text-slate-600">{notification.message}</p>
                        <p className="mt-2 flex items-center gap-1.5 text-[11px] font-medium text-slate-400">
                          <Clock className="h-3.5 w-3.5" />
                          {formatRelativeTime(notification.createdAt)}
                        </p>
                      </div>
                    </div>
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
      ) : null}
    </div>
  );
};

export default ManagerNotificationsBell;
