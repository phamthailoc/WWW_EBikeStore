package com.ebike.service.order;

import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.model.entity.product.ProductSpecification;
import com.ebike.model.entity.product.VehicleType;

/**
 * V18. Vehicles are sold with a frame number and an engine number each (needed for the invoice, for the owner's own
 * registration on VNeID and for the warranty); batteries and accessories are not.
 */
public final class OrderUnits {
    private OrderUnits() {
    }

    public static boolean requiresNumbers(OrderItem item) {
        ProductSpecification specification = item.getProduct() == null ? null : item.getProduct().getSpecification();
        VehicleType type = specification == null ? null : specification.getVehicleType();
        return type != null && type != VehicleType.BATTERY && type != VehicleType.ACCESSORY;
    }

    /** True when every vehicle of the order has its numbers: a line of three bikes needs three entries. */
    public static boolean isComplete(Order order) {
        return order.getItems().stream()
            .filter(OrderUnits::requiresNumbers)
            .allMatch(item -> item.getUnits().size() >= (item.getQuantity() == null ? 0 : item.getQuantity()));
    }
}
