import { NavLink, Outlet } from "react-router-dom";

const customerNavItems = [
  { to: "/customer", label: "Overview", end: true },
  { to: "/customer/orders", label: "Orders" },
  { to: "/customer/payments", label: "Thanh toán" },
  { to: "/customer/notifications", label: "Thông báo" },
  { to: "/customer/profile", label: "Profile" }
];

const CustomerLayout = () => (
  <section className="mx-auto flex w-full max-w-screen-2xl flex-col gap-6 px-4 pb-12 pt-28 sm:px-6 lg:px-12">
    <div className="flex max-w-[760px] flex-col gap-3">
      <span className="inline-block text-[0.72rem] font-bold uppercase tracking-[0.18em] text-primary">Customer Area</span>
      <h1 className="[font-family:Bahnschrift,'Arial_Narrow',sans-serif] text-[clamp(2rem,3vw,2.8rem)] font-extrabold leading-tight text-[#111]">
        Trung tâm tài khoản KINETIC.
      </h1>
      <p className="m-0 max-w-2xl leading-[1.7] text-[#666]">
        Theo dõi đơn hàng, quản lý thanh toán, thông báo và thông tin cá nhân trong một không gian gọn gàng.
      </p>
    </div>

    <div className="flex flex-wrap gap-2 rounded-lg border border-[rgba(17,17,17,0.08)] bg-white p-2 shadow-sm">
      {customerNavItems.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.end}
          className={({ isActive }) =>
            `rounded-md px-4 py-2.5 text-sm font-semibold transition ${isActive ? "bg-primary text-white shadow-sm" : "text-[#666] hover:bg-black/5 hover:text-[#111]"}`
          }
        >
          {item.label}
        </NavLink>
      ))}
    </div>

    <Outlet />
  </section>
);

export default CustomerLayout;
