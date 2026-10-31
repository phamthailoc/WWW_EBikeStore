import SectionShell from "../../components/common/SectionShell";

const CustomerDashboardPage = () => (
  <SectionShell
    eyebrow="Bảng điều khiển khách hàng"
    title="Tổng quan nhanh về hoạt động gần đây của bạn."
    description="Trang này sau này có thể hiển thị các đơn hàng gần đây, sản phẩm đã lưu, lịch sử chatbot và gợi ý dịch vụ cho người dùng quay lại."
  >
    <div className="py-8 px-6">
      <div className="p-6 bg-gray-50 rounded-lg text-center text-gray-600">
        Các widget bảng điều khiển khách hàng sẽ được thêm tại đây.
      </div>
    </div>
  </SectionShell>
);

export default CustomerDashboardPage;
