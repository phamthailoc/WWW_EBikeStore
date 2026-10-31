import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "@ebike/shared-code/hooks";
import ManagerSidebar from "../components/manager/ManagerSidebar";
import ManagerTopNav from "../components/manager/ManagerTopNav";

const ManagerLayout = () => {
  const { isBootstrapping, isAuthenticated, user } = useAuth();

  if (isBootstrapping) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 text-sm font-semibold text-slate-500">
        Đang tải phiên làm việc...
      </div>
    );
  }

  if (!isAuthenticated || (!user?.roles.includes("MANAGER") && !user?.roles.includes("ADMIN"))) {
    return <Navigate to="/auth" replace />;
  }

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <ManagerSidebar />
      <div className="ml-[248px] min-h-screen">
        <ManagerTopNav />
        <main className="px-6 py-6">
          <div className="mx-auto max-w-[1400px]">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
};

export default ManagerLayout;
