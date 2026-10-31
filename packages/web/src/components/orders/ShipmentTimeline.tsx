import type { Order } from "@ebike/shared-code/types";

export type ShipmentTimelineStep = {
  key: string;
  label: string;
  timestamp?: string | null;
  completed: boolean;
  active: boolean;
};

const formatDateTime = (value?: string | null) =>
  value
    ? new Intl.DateTimeFormat("vi-VN", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit"
      }).format(new Date(value))
    : null;

const resolveStatusIndex = (shipmentStatus?: string | null) => {
  switch (shipmentStatus) {
    case "DELIVERED":
      return 3;
    case "SHIPPED":
    case "IN_TRANSIT":
      return 2;
    case "PREPARING":
      return 1;
    default:
      return 0;
  }
};

export const buildShipmentTimeline = (order: Order) => {
  if (order.status === "CANCELLED") {
    return {
      empty: false,
      cancelled: true,
      lastUpdated: order.cancellationRequestedAt || order.updatedAt,
      steps: [
        {
          key: "CANCELLED",
          label: "Đã hủy",
          timestamp: order.cancellationRequestedAt || order.updatedAt,
          completed: true,
          active: true
        }
      ] satisfies ShipmentTimelineStep[]
    };
  }

  if (!order.shipment) {
    return {
      empty: true,
      cancelled: false,
      lastUpdated: order.updatedAt,
      steps: [] as ShipmentTimelineStep[]
    };
  }

  const statusIndex = resolveStatusIndex(order.shipment.shipmentStatus);
  const steps: ShipmentTimelineStep[] = [
    {
      key: "PENDING",
      label: "Chờ xử lý",
      timestamp: order.createdAt,
      completed: statusIndex >= 0,
      active: statusIndex === 0
    },
    {
      key: "PREPARING",
      label: "Đang chuẩn bị",
      timestamp: statusIndex >= 1 ? order.updatedAt : null,
      completed: statusIndex >= 1,
      active: statusIndex === 1
    },
    {
      key: "SHIPPING",
      label: "Đang giao",
      timestamp: order.shipment.shippedAt,
      completed: statusIndex >= 2,
      active: statusIndex === 2
    },
    {
      key: "DELIVERED",
      label: "Đã giao",
      timestamp: order.shipment.deliveredAt,
      completed: statusIndex >= 3,
      active: statusIndex === 3
    }
  ];

  const lastUpdated = order.shipment.deliveredAt || order.shipment.shippedAt || order.updatedAt;

  return {
    empty: false,
    cancelled: false,
    lastUpdated,
    steps
  };
};

type ShipmentTimelineProps = {
  order: Order;
  compact?: boolean;
};

const ShipmentTimeline = ({ order, compact = false }: ShipmentTimelineProps) => {
  const timeline = buildShipmentTimeline(order);

  if (timeline.empty) {
    return (
      <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 px-4 py-5 text-sm text-slate-500">
        Đơn hàng chưa có thông tin giao hàng. Vui lòng quay lại sau khi showroom cập nhật.
      </div>
    );
  }

  return (
    <div className={compact ? "space-y-4" : "space-y-5"}>
      <div className="space-y-5">
        {timeline.steps.map((step, index) => (
          <div key={step.key} className="flex items-start gap-3">
            <div className="flex flex-col items-center">
              <div
                className={`mt-1 h-3 w-3 rounded-full ${
                  step.completed ? "bg-[#003b93]" : "bg-slate-200"
                } ${step.active ? "ring-4 ring-blue-100" : ""}`}
              />
              {index < timeline.steps.length - 1 && <div className="mt-1 h-10 w-px bg-slate-200" />}
            </div>
            <div className="min-w-0 flex-1">
              <p className={`text-sm font-semibold ${step.active ? "text-slate-950" : "text-slate-500"}`}>{step.label}</p>
              {step.timestamp && (
                <p className="mt-1 text-xs text-slate-400">{formatDateTime(step.timestamp)}</p>
              )}
            </div>
          </div>
        ))}
      </div>
      {order.shipment?.trackingNumber && (
        <p className="text-sm text-slate-600">
          Mã vận đơn: <span className="font-semibold text-slate-900">{order.shipment.trackingNumber}</span>
        </p>
      )}
      <p className="text-xs text-slate-400">Cập nhật gần nhất: {formatDateTime(timeline.lastUpdated) || "-"}</p>
    </div>
  );
};

export default ShipmentTimeline;
