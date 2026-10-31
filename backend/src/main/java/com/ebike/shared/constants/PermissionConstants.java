package com.ebike.shared.constants;

public final class PermissionConstants {

    private PermissionConstants() {
    }

    // Capability groups used by controllers, services, and security rules.
    public static final class Guest {
        public static final String PRODUCT_VIEW = "product:view";
        public static final String PRODUCT_SEARCH = "product:search";
        public static final String CATEGORY_VIEW = "category:view";
        public static final String REVIEW_VIEW = "review:view";
        public static final String ORDER_CREATE = "order:create";
        public static final String PAYMENT_CREATE = "payment:create";
        public static final String CHATBOT_USE = "chatbot:use";

        private Guest() {
        }
    }

    public static final class Customer {
        public static final String ORDER_CREATE = "order:create";
        public static final String ORDER_VIEW_OWN = "order:view-own";
        public static final String ORDER_CANCEL_OWN = "order:cancel-own";
        public static final String PAYMENT_CREATE = "payment:create";
        public static final String PAYMENT_VIEW_OWN = "payment:view-own";
        public static final String FAVORITE_VIEW = "favorite:view";
        public static final String FAVORITE_UPDATE = "favorite:update";
        public static final String PROFILE_VIEW = "profile:view";
        public static final String PROFILE_UPDATE = "profile:update";
        public static final String REVIEW_CREATE = "review:create";

        private Customer() {
        }
    }

    public static final class ProductManagement {
        public static final String PRODUCT_CREATE = "product:create";
        public static final String PRODUCT_UPDATE = "product:update";
        public static final String PRODUCT_DELETE = "product:delete";
        public static final String PRODUCT_MANAGE_STOCK = "product:manage-stock";
        public static final String CATEGORY_MANAGE = "category:manage";
        public static final String REVIEW_MODERATE = "review:moderate";

        private ProductManagement() {
        }
    }

    public static final class OrderManagement {
        public static final String ORDER_VIEW_ALL = "order:view-all";
        public static final String ORDER_UPDATE_STATUS = "order:update-status";
        public static final String ORDER_ASSIGN_SHIPMENT = "order:assign-shipment";
        public static final String PAYMENT_VERIFY = "payment:verify";
        public static final String PAYMENT_REFUND = "payment:refund";
        public static final String SHIPMENT_MANAGE = "shipment:manage";

        private OrderManagement() {
        }
    }

    public static final class UserManagement {
        public static final String USER_VIEW = "user:view";
        public static final String USER_MANAGE = "user:manage";

        private UserManagement() {
        }
    }

    public static final class ChatbotManagement {
        public static final String CHATBOT_USE = "chatbot:use";
        public static final String CHATBOT_VIEW_HISTORY = "chatbot:view-history";
        public static final String CHATBOT_MANAGE_FAQ = "chatbot:manage-faq";
        public static final String CHATBOT_MANAGE_KNOWLEDGE = "chatbot:manage-knowledge";
        public static final String CHATBOT_CONFIGURE = "chatbot:configure";

        private ChatbotManagement() {
        }
    }

    public static final class Reporting {
        public static final String DASHBOARD_VIEW = "dashboard:view";
        public static final String REPORT_VIEW = "report:view";

        private Reporting() {
        }
    }

    public static final class AccessControl {
        public static final String ROLE_MANAGE = "role:manage";
        public static final String PERMISSION_MANAGE = "permission:manage";

        private AccessControl() {
        }
    }

    public static final class SystemAdministration {
        public static final String SYSTEM_CONFIG = "system:config";
        public static final String AUDIT_LOG_VIEW = "audit-log:view";

        private SystemAdministration() {
        }
    }

    // Default bundles by role. Keep these for role mapping and documentation.
    public static final class Manager {
        public static final String PRODUCT_VIEW = Guest.PRODUCT_VIEW;
        public static final String PRODUCT_SEARCH = Guest.PRODUCT_SEARCH;
        public static final String CATEGORY_VIEW = Guest.CATEGORY_VIEW;
        public static final String REVIEW_VIEW = Guest.REVIEW_VIEW;
        public static final String FAVORITE_VIEW = Customer.FAVORITE_VIEW;
        public static final String FAVORITE_UPDATE = Customer.FAVORITE_UPDATE;
        public static final String PRODUCT_CREATE = ProductManagement.PRODUCT_CREATE;
        public static final String PRODUCT_UPDATE = ProductManagement.PRODUCT_UPDATE;
        public static final String PRODUCT_DELETE = ProductManagement.PRODUCT_DELETE;
        public static final String PRODUCT_MANAGE_STOCK = ProductManagement.PRODUCT_MANAGE_STOCK;
        public static final String CATEGORY_MANAGE = ProductManagement.CATEGORY_MANAGE;
        public static final String REVIEW_MODERATE = ProductManagement.REVIEW_MODERATE;
        public static final String ORDER_VIEW_ALL = OrderManagement.ORDER_VIEW_ALL;
        public static final String ORDER_UPDATE_STATUS = OrderManagement.ORDER_UPDATE_STATUS;
        public static final String ORDER_ASSIGN_SHIPMENT = OrderManagement.ORDER_ASSIGN_SHIPMENT;
        public static final String PAYMENT_VERIFY = OrderManagement.PAYMENT_VERIFY;
        public static final String PAYMENT_REFUND = OrderManagement.PAYMENT_REFUND;
        public static final String SHIPMENT_MANAGE = OrderManagement.SHIPMENT_MANAGE;
        public static final String USER_VIEW = UserManagement.USER_VIEW;
        public static final String USER_MANAGE = UserManagement.USER_MANAGE;
        public static final String CHATBOT_USE = ChatbotManagement.CHATBOT_USE;
        public static final String CHATBOT_VIEW_HISTORY = ChatbotManagement.CHATBOT_VIEW_HISTORY;
        public static final String CHATBOT_MANAGE_FAQ = ChatbotManagement.CHATBOT_MANAGE_FAQ;
        public static final String CHATBOT_MANAGE_KNOWLEDGE = ChatbotManagement.CHATBOT_MANAGE_KNOWLEDGE;
        public static final String DASHBOARD_VIEW = Reporting.DASHBOARD_VIEW;
        public static final String REPORT_VIEW = Reporting.REPORT_VIEW;

        private Manager() {
        }
    }

    public static final class Admin {
        public static final String PRODUCT_VIEW = Guest.PRODUCT_VIEW;
        public static final String PRODUCT_SEARCH = Guest.PRODUCT_SEARCH;
        public static final String CATEGORY_VIEW = Guest.CATEGORY_VIEW;
        public static final String REVIEW_VIEW = Guest.REVIEW_VIEW;
        public static final String ORDER_CREATE = Customer.ORDER_CREATE;
        public static final String ORDER_VIEW_OWN = Customer.ORDER_VIEW_OWN;
        public static final String ORDER_CANCEL_OWN = Customer.ORDER_CANCEL_OWN;
        public static final String PAYMENT_CREATE = Customer.PAYMENT_CREATE;
        public static final String PAYMENT_VIEW_OWN = Customer.PAYMENT_VIEW_OWN;
        public static final String FAVORITE_VIEW = Customer.FAVORITE_VIEW;
        public static final String FAVORITE_UPDATE = Customer.FAVORITE_UPDATE;
        public static final String PROFILE_VIEW = Customer.PROFILE_VIEW;
        public static final String PROFILE_UPDATE = Customer.PROFILE_UPDATE;
        public static final String REVIEW_CREATE = Customer.REVIEW_CREATE;
        public static final String PRODUCT_CREATE = ProductManagement.PRODUCT_CREATE;
        public static final String PRODUCT_UPDATE = ProductManagement.PRODUCT_UPDATE;
        public static final String PRODUCT_DELETE = ProductManagement.PRODUCT_DELETE;
        public static final String PRODUCT_MANAGE_STOCK = ProductManagement.PRODUCT_MANAGE_STOCK;
        public static final String CATEGORY_MANAGE = ProductManagement.CATEGORY_MANAGE;
        public static final String REVIEW_MODERATE = ProductManagement.REVIEW_MODERATE;
        public static final String ORDER_VIEW_ALL = OrderManagement.ORDER_VIEW_ALL;
        public static final String ORDER_UPDATE_STATUS = OrderManagement.ORDER_UPDATE_STATUS;
        public static final String ORDER_ASSIGN_SHIPMENT = OrderManagement.ORDER_ASSIGN_SHIPMENT;
        public static final String PAYMENT_VERIFY = OrderManagement.PAYMENT_VERIFY;
        public static final String PAYMENT_REFUND = OrderManagement.PAYMENT_REFUND;
        public static final String SHIPMENT_MANAGE = OrderManagement.SHIPMENT_MANAGE;
        public static final String USER_VIEW = UserManagement.USER_VIEW;
        public static final String USER_MANAGE = UserManagement.USER_MANAGE;
        public static final String ROLE_MANAGE = AccessControl.ROLE_MANAGE;
        public static final String PERMISSION_MANAGE = AccessControl.PERMISSION_MANAGE;
        public static final String CHATBOT_USE = ChatbotManagement.CHATBOT_USE;
        public static final String CHATBOT_VIEW_HISTORY = ChatbotManagement.CHATBOT_VIEW_HISTORY;
        public static final String CHATBOT_MANAGE_FAQ = ChatbotManagement.CHATBOT_MANAGE_FAQ;
        public static final String CHATBOT_MANAGE_KNOWLEDGE = ChatbotManagement.CHATBOT_MANAGE_KNOWLEDGE;
        public static final String CHATBOT_CONFIGURE = ChatbotManagement.CHATBOT_CONFIGURE;
        public static final String DASHBOARD_VIEW = Reporting.DASHBOARD_VIEW;
        public static final String REPORT_VIEW = Reporting.REPORT_VIEW;
        public static final String SYSTEM_CONFIG = SystemAdministration.SYSTEM_CONFIG;
        public static final String AUDIT_LOG_VIEW = SystemAdministration.AUDIT_LOG_VIEW;

        private Admin() {
        }
    }
}
