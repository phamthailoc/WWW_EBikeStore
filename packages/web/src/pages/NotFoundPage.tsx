import { NavLink } from "react-router-dom";
import { Home, ArrowLeft, Search } from "lucide-react";

const NotFoundPage = () => {
  const suggestions = [
    { title: "Trang chủ", path: "/", icon: "🏠" },
    { title: "Sản phẩm", path: "/products", icon: "🚴" },
    { title: "Cố vấn thông minh", path: "/chatbot", icon: "🤖" },
    { title: "Đăng nhập", path: "/login", icon: "🔐" }
  ];

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900 flex items-center justify-center px-4 sm:px-6 lg:px-8 relative overflow-hidden">
      {/* Decorative Elements */}
      <div className="absolute top-0 left-0 w-96 h-96 bg-blue-500/20 rounded-full mix-blend-multiply filter blur-3xl opacity-50 animate-blob"></div>
      <div className="absolute bottom-0 right-0 w-96 h-96 bg-purple-500/20 rounded-full mix-blend-multiply filter blur-3xl opacity-50 animate-blob animation-delay-4000"></div>

      <div className="max-w-2xl w-full relative z-10">
        {/* Main Content */}
        <div className="text-center mb-12">
          {/* Large 404 */}
          <div className="mb-8">
            <div className="text-9xl font-bold bg-gradient-to-r from-blue-400 via-purple-400 to-pink-400 bg-clip-text text-transparent mb-4">
              404
            </div>
            <div className="text-7xl mb-6">🚴‍♂️</div>
          </div>

          {/* Heading */}
          <h1 className="text-5xl md:text-6xl font-bold text-white mb-4 leading-tight">
            Oops! Trang không tìm thấy
          </h1>

          <p className="text-xl text-slate-300 mb-4 leading-relaxed">
            Có vẻ như trang này đã bị lệch khỏi đường. Đừng lo lắng - cố vấn thông minh của chúng tôi có thể giúp bạn quay lại đúng hành trình!
          </p>

          <p className="text-slate-400 text-lg mb-8">
            Trang bạn đang tìm kiếm có thể đã bị di chuyển hoặc xóa.
          </p>
        </div>

        {/* Action Buttons */}
        <div className="flex flex-col sm:flex-row gap-4 justify-center mb-12">
          <NavLink
            to="/"
            className="inline-flex items-center justify-center gap-2 px-8 py-4 bg-blue-600 hover:bg-blue-700 text-white rounded-lg font-semibold transition duration-200 shadow-lg hover:shadow-xl"
          >
            <Home className="w-5 h-5" />
            <span>Quay về trang chủ</span>
          </NavLink>

          <button
            onClick={() => window.history.back()}
            className="inline-flex items-center justify-center gap-2 px-8 py-4 border-2 border-blue-600 text-blue-400 hover:text-blue-300 hover:border-blue-400 rounded-lg font-semibold transition duration-200"
          >
            <ArrowLeft className="w-5 h-5" />
            <span>Quay lại</span>
          </button>
        </div>

        {/* Suggestions */}
        <div>
          <p className="text-slate-300 font-semibold text-center mb-6">Bạn muốn đi đâu?</p>
          
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            {suggestions.map((suggestion) => (
              <NavLink
                key={suggestion.path}
                to={suggestion.path}
                className="p-6 bg-slate-700/50 hover:bg-slate-600 border border-slate-600 hover:border-blue-400 rounded-xl transition duration-200 text-center group"
              >
                <div className="text-4xl mb-3 group-hover:scale-110 transition duration-300 transform">
                  {suggestion.icon}
                </div>
                <p className="text-white font-semibold group-hover:text-blue-400 transition">
                  {suggestion.title}
                </p>
              </NavLink>
            ))}
          </div>
        </div>

        {/* Error Details */}
        <div className="mt-12 p-6 bg-slate-700/30 border border-slate-600 rounded-xl backdrop-blur-sm">
          <div className="flex items-start gap-3 mb-3">
            <Search className="w-5 h-5 text-blue-400 flex-shrink-0 mt-1" />
            <div>
              <p className="text-white font-semibold mb-2">Cần giúp đỡ tìm thứ gì đó?</p>
              <p className="text-slate-300 text-sm">
                Cố vấn thông minh của chúng tôi có thể giúp bạn tìm chiếc xe điện hoàn hảo chỉ trong vài câu hỏi. 
                Không cần điều hướng thủ công!
              </p>
              <NavLink
                to="/chatbot"
                className="inline-block mt-3 text-blue-400 hover:text-blue-300 font-semibold text-sm"
              >
                Hỏi cố vấn thông minh →
              </NavLink>
            </div>
          </div>
        </div>

        {/* Fun Message */}
        <div className="mt-8 text-center">
          <p className="text-slate-400 text-sm italic">
            Pro tip: Our e-bikes won't get lost, but our pages might! 😄
          </p>
        </div>
      </div>
    </div>
  );
};

export default NotFoundPage;