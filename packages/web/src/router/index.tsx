import { createBrowserRouter } from "react-router-dom";

// Layouts
import AdminLayout from "../layouts/AdminLayout";
import CustomerLayout from "../layouts/CustomerLayout";
import ManagerLayout from "../layouts/ManagerLayout";
import Layout from "../components/common/Layout";

// Pages
import AdminDashboardPage from "../pages/admin/AdminDashboardPage";
import AdminAccountsAccessPage from "../pages/admin/AdminAccountsAccessPage";
import AdminPricingPromotionsPage from "../pages/admin/AdminPricingPromotionsPage";
import AdminProductsPage from "../pages/admin/AdminProductsPage";
import AdminUsersPage from "../pages/admin/AdminUsersPage";
import ChatbotPage from "../pages/ChatbotPage";
import CheckoutPage from "../pages/CheckoutPage";
import FavoritesPage from "../pages/FavoritesPage";
import HomePage from "../pages/HomePage";
import LoginPage from "../pages/LoginPageApi";
import NotFoundPage from "../pages/NotFoundPage";
import OrderSuccessPage from "../pages/OrderSuccessPage";
import PaymentReturnPage from "../pages/PaymentReturnPage";
import ProductDetailPage from "../pages/ProductDetailPage";
import ProductsPage from "../pages/ProductsPage";
import CompareProductsPage from "../pages/CompareProductsPage";
import SignupPage from "../pages/SignupPage";
import SupportPage from "../pages/SupportPage";
import CustomerDashboardPage from "../pages/customer/CustomerDashboardRealPage";
import CustomerOrdersPage from "../pages/customer/CustomerOrdersSafePage";
import CustomerOrderDetailPage from "../pages/customer/CustomerOrderDetailPage";
import CustomerNotificationsPage from "../pages/customer/CustomerNotificationsPage";
import CustomerPaymentHistoryPage from "../pages/customer/CustomerPaymentHistoryPage";
import CustomerProfilePage from "../pages/customer/CustomerProfilePage";
import ManagerCustomersPage from "../pages/manager/ManagerCustomersPage";
import ManagerDashboardPage from "../pages/manager/ManagerDashboardPage";
import ManagerInventoryPage from "../pages/manager/ManagerInventoryPage";
import ManagerOrderDetailPage from "../pages/manager/ManagerOrderDetailPage";
import ManagerOrdersPage from "../pages/manager/ManagerOrdersPage";
import ManagerPaymentsPage from "../pages/manager/ManagerPaymentsPage";
import ManagerProductsPage from "../pages/manager/ManagerProductsPage";
import ManagerRevenueReportPage from "../pages/manager/ManagerRevenueReportPage";

const router = createBrowserRouter([
  {
    path: "/",
    element: <Layout />,
    errorElement: <NotFoundPage />,
    children: [
      // Guest
      { index: true, element: <HomePage /> },
      { path: "auth", element: <LoginPage /> },
      { path: "signup", element: <SignupPage /> },
      { path: "products", element: <ProductsPage /> },
      { path: "models", element: <ProductsPage /> },
      { path: "product/:id", element: <ProductDetailPage /> },
      { path: "models/:id", element: <ProductDetailPage /> },
      { path: "favorites", element: <FavoritesPage /> },
      { path: "checkout", element: <CheckoutPage /> },
      { path: "checkout/success", element: <OrderSuccessPage /> },
      { path: "payment/return", element: <PaymentReturnPage /> },
      { path: "support", element: <SupportPage /> },
      { path: "compare", element: <CompareProductsPage /> },
      { path: "chatbot", element: <ChatbotPage /> }
    ]
  },
  {
    path: "/customer",
    element: <Layout />,
    errorElement: <NotFoundPage />,
    children: [
      {
        path: "",
        element: <CustomerLayout />,
        children: [
          { index: true, element: <CustomerDashboardPage /> },
          { path: "orders", element: <CustomerOrdersPage /> },
          { path: "orders/:id", element: <CustomerOrderDetailPage /> },
          { path: "payments", element: <CustomerPaymentHistoryPage /> },
          { path: "notifications", element: <CustomerNotificationsPage /> },
          { path: "profile", element: <CustomerProfilePage /> }
        ]
      }
    ]
  },
  {
    path: "/admin",
    errorElement: <NotFoundPage />,
    element: <AdminLayout />,
    children: [
      { index: true, element: <AdminDashboardPage /> },
      { path: "pricing", element: <AdminPricingPromotionsPage /> },
      { path: "accounts", element: <AdminAccountsAccessPage /> },
      { path: "products", element: <AdminProductsPage /> },
      { path: "inventory", element: <ManagerInventoryPage /> },
      { path: "users", element: <AdminUsersPage /> }
    ]
  },
  {
    path: "/manager",
    element: <ManagerLayout />,
    children: [
      { index: true, element: <ManagerDashboardPage /> },
      { path: "orders", element: <ManagerOrdersPage /> },
      { path: "orders/:id", element: <ManagerOrderDetailPage /> },
      { path: "payments", element: <ManagerPaymentsPage /> },
      { path: "customers", element: <ManagerCustomersPage /> },
      { path: "inventory", element: <ManagerInventoryPage /> },
      { path: "products", element: <ManagerProductsPage /> },
      { path: "reports", element: <ManagerRevenueReportPage /> }
    ]
  },
  {
    path: "*",
    element: <NotFoundPage />
  }
]);

export default router;
