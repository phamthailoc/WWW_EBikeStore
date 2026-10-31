import SectionShell from "../../components/common/SectionShell";

const AdminProductsPage = () => (
  <SectionShell
    eyebrow="Sản phẩm quản trị"
    title="Quản lý danh mục xe điện từ tuyến đường quản trị riêng."
    description="Tuyến đường này sau này có thể lưu trữ quản lý bảng sản phẩm, biểu mẫu upsert, tải lên phương tiện và kiểm soát kho."
  >
    <div className="py-8 px-6">
      <div className="p-6 bg-gray-50 rounded-lg text-center text-gray-600">
        Giao diện quản lý sản phẩm quản trị sẽ được đặt tại đây.
      </div>
    </div>
  </SectionShell>
);

export default AdminProductsPage;
