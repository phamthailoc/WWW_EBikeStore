import { useCallback, useEffect, useMemo, useState } from "react";
import { favoritesAPI } from "../api";
import type { Product } from "../types";
import { useAuth } from "./useAuth";

const getFavoriteErrorMessage = (error: unknown, fallbackMessage: string) => {
  if (error instanceof Error) {
    return error.message;
  }

  const status = (error as { response?: { status?: number } } | null | undefined)?.response?.status;
  if (status === 401) {
    return "Vui lòng đăng nhập để sử dụng danh sách yêu thích.";
  }

  return fallbackMessage;
};

export const useFavorites = () => {
  const { isAuthenticated, isBootstrapping } = useAuth();
  const [favorites, setFavorites] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pendingProductIds, setPendingProductIds] = useState<number[]>([]);

  const favoriteIdSet = useMemo(() => new Set(favorites.map((product) => product.id)), [favorites]);

  const setPending = useCallback((productId: number, isPending: boolean) => {
    setPendingProductIds((current) => {
      if (isPending) {
        return current.includes(productId) ? current : [...current, productId];
      }

      return current.filter((pendingId) => pendingId !== productId);
    });
  }, []);

  const loadFavorites = useCallback(async () => {
    if (!isAuthenticated) {
      setFavorites([]);
      setLoading(false);
      setError(null);
      return;
    }

    setLoading(true);
    try {
      const data = await favoritesAPI.list();
      setFavorites(data);
      setError(null);
    } catch (fetchError) {
      setFavorites([]);
      setError(getFavoriteErrorMessage(fetchError, "Không thể tải danh sách yêu thích."));
    } finally {
      setLoading(false);
    }
  }, [isAuthenticated]);

  useEffect(() => {
    if (isBootstrapping) {
      return;
    }

    void loadFavorites();
  }, [isBootstrapping, loadFavorites]);

  const addFavorite = useCallback(
    async (productId: number) => {
      if (!isAuthenticated) {
        setError("Vui lòng đăng nhập để thêm vào danh sách yêu thích.");
        return false;
      }

      setPending(productId, true);
      try {
        const savedProduct = await favoritesAPI.add(productId);
        setFavorites((current) => {
          const nextItems = current.filter((item) => item.id !== productId);
          return [savedProduct, ...nextItems];
        });
        setError(null);
        return true;
      } catch (addError) {
        setError(getFavoriteErrorMessage(addError, "Không thể thêm sản phẩm vào yêu thích."));
        return false;
      } finally {
        setPending(productId, false);
      }
    },
    [isAuthenticated, setPending]
  );

  const removeFavorite = useCallback(
    async (productId: number) => {
      if (!isAuthenticated) {
        setError("Vui lòng đăng nhập để cập nhật danh sách yêu thích.");
        return false;
      }

      setPending(productId, true);
      try {
        await favoritesAPI.remove(productId);
        setFavorites((current) => current.filter((product) => product.id !== productId));
        setError(null);
        return true;
      } catch (removeError) {
        setError(getFavoriteErrorMessage(removeError, "Không thể xóa sản phẩm khỏi yêu thích."));
        return false;
      } finally {
        setPending(productId, false);
      }
    },
    [isAuthenticated, setPending]
  );

  const toggleFavorite = useCallback(
    async (productId: number) => {
      if (favoriteIdSet.has(productId)) {
        return removeFavorite(productId);
      }

      return addFavorite(productId);
    },
    [addFavorite, favoriteIdSet, removeFavorite]
  );

  const isFavorite = useCallback((productId: number) => favoriteIdSet.has(productId), [favoriteIdSet]);
  const isPending = useCallback((productId: number) => pendingProductIds.includes(productId), [pendingProductIds]);

  return {
    favorites,
    loading,
    error,
    isAuthenticated,
    isBootstrapping,
    favoriteIdSet,
    pendingProductIds,
    isFavorite,
    isPending,
    loadFavorites,
    addFavorite,
    removeFavorite,
    toggleFavorite,
    clearError: () => setError(null)
  };
};