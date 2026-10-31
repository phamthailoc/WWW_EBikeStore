import SectionShell from "../../components/common/SectionShell";

const AdminUsersPage = () => (
  <SectionShell
    eyebrow="Người dùng quản trị"
    title="Xem xét các tài khoản người dùng, quyền và các hành động tài khoản liên quan đến hỗ trợ."
    description="Tách biệt tuyến đường này sớm giúp dễ dàng tiến hóa hướng tới công cụ quản trị nhận biết PBAC sau này."
  >
    <div className="py-8 px-6">
      <div className="p-6 bg-gray-50 rounded-lg text-center text-gray-600">
        Bảng quản lý người dùng và kiểm soát quyền sẽ được thêm tại đây.
      </div>
    </div>
  </SectionShell>
);

export default AdminUsersPage;
