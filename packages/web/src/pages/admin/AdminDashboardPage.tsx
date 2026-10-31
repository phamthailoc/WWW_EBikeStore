import SectionShell from "../../components/common/SectionShell";

const AdminDashboardPage = () => (
  <SectionShell
    eyebrow="Bảng điều khiển quản trị"
    title="Giám sát hoạt động sản phẩm, khách hàng và chatbot từ một nơi."
    description="Tuyến đường này cung cấp cho nhóm một nhánh quản trị dành riêng cho báo cáo trong tương lai và kiểm soát hoạt động."
  >
    <div className="py-8 px-6">
      <div className="p-6 bg-gray-50 rounded-lg text-center text-gray-600">
        KPI quản trị, biểu đồ và tóm tắt hoạt động sẽ được thêm tại đây.
      </div>
    </div>
  </SectionShell>
);

export default AdminDashboardPage;
